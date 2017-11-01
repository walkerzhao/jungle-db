package com.tencent.jungle.db.transaction;

/*
 *    Copyright 2010-2014 The MyBatis Team
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import static java.lang.String.format;
import static java.lang.Thread.currentThread;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Arrays;


import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.configuration.Configuration;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.mybatis.guice.transactional.Isolation;
import org.mybatis.guice.transactional.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.mysql.jdbc.StringUtils;
import com.tencent.jungle.db.datasource.DataSources;
import com.tencent.jungle.db.datasource.DynamicDataSources;

/**
 * Method interceptor for {@link Transactional} annotation.
 *
 * @version $Id$
 */
public final class TransactionalMethodInterceptor implements MethodInterceptor {

	private static final Class<?>[] CAUSE_TYPES = new Class[] { Throwable.class };

	private static final Class<?>[] MESSAGE_CAUSE_TYPES = new Class[] {
			String.class, Throwable.class };

	/**
	 * This class logger.
	 */
	private final static Logger log=LoggerFactory.getLogger("SQL_LOG_INTERCEPTOR");

	/**
	 * The {@code SqlSessionManager} reference.
	 */
	@Inject
	private DataSources sqlSessionManager;
	private DynamicDataSources dynamicDataSource;
	//private Configuration config;
	/**
	 * Sets the SqlSessionManager instance.
	 *
	 * @param sqlSessionManager
	 *            the SqlSessionManager instance.
	 */
	@Inject
	public void setSqlSessionManager(DataSources sqlSessionManager,Injector injector ) {
		this.sqlSessionManager = sqlSessionManager;
		try{
			this.dynamicDataSource = injector.getInstance(DynamicDataSources.class);
		}catch (Exception e) {
			log.error("injector has not dynamicDataSource",e);
		}
		/*try{
			this.config = injector.getInstance(Configuration.class);
		}catch (Exception e) {
			log.error("injector has not Configuration",e);
		}*/
	}

	/**
	 * {@inheritDoc}
	 */
	public Object invoke(MethodInvocation invocation) throws Throwable {
		//System.out
		//		.println("++++++++++++++++++++++++++++++++interceptor called");
		Method interceptedMethod = invocation.getMethod();
		
		DBTransactional transactional = interceptedMethod
				.getAnnotation(DBTransactional.class);

		// The annotation may be present at the class level instead
		if (transactional == null) {
			transactional = interceptedMethod.getDeclaringClass()
					.getAnnotation(DBTransactional.class);
		}

		String debugPrefix = null;
		if (this.log.isDebugEnabled()) {
			debugPrefix = format("[Intercepted method: %s]",
					interceptedMethod.toGenericString());
		}

		boolean isSessionInherited = this.sqlSessionManager
				.isManagedSessionStarted();

		if (isSessionInherited) {
			if (log.isDebugEnabled()) {
				log.debug(format("%s - SqlSession already set for thread: %s",
						debugPrefix, currentThread().getId()));
			}
		} else {
			if (log.isDebugEnabled()) {
				log.debug(format(
						"%s - SqlSession not set for thread: %s, creating a new one",
						debugPrefix, currentThread().getId()));
			}
			String db = transactional.dataSource();
			if (StringUtils.isNullOrEmpty(db)) {
				if(transactional.dynamicSource()&&this.dynamicDataSource!=null){
					Object[] args = invocation.getArguments();
					if(args!=null&&args.length>0){
						if(args[0] instanceof String){
							Connection con = dynamicDataSource.getDataSource((String)args[0]).getConnection();
							Isolation t = transactional.isolation();
							if (Isolation.DEFAULT != t) {
								con.setTransactionIsolation(transactional.isolation()
										.getTransactionIsolationLevel().getLevel());
							}
							sqlSessionManager.startManagedSession(
									transactional.executorType(), con);
						}
					}
				}else{
					sqlSessionManager.startManagedSession(transactional
							.executorType(), transactional.isolation()
							.getTransactionIsolationLevel());
				}
				
			} else {
				/*if(this.config!=null){
					String db_tmp = this.config.getString("jungle.db.datasource."+db);
					if(StringUtils.isNullOrEmpty(db_tmp)){
						db = db_tmp;
					}
				}*/
				Connection con = sqlSessionManager.get(db).getConnection();
				Isolation t = transactional.isolation();
				if (Isolation.DEFAULT != t) {
					con.setTransactionIsolation(transactional.isolation()
							.getTransactionIsolationLevel().getLevel());
				}
				sqlSessionManager.startManagedSession(
						transactional.executorType(), con);
			}

		}

		Object object = null;
		boolean needsRollback = transactional.rollbackOnly();
		try {
			object = invocation.proceed();
		} catch (Throwable t) {
			needsRollback = true;
			throw convertThrowableIfNeeded(invocation, transactional, t);
		} finally {
			if (!isSessionInherited) {
				try {
					if (needsRollback) {
						if (log.isDebugEnabled()) {
							log.debug(debugPrefix + " - SqlSession of thread: "
									+ currentThread().getId() + " rolling back");
						}

						sqlSessionManager.rollback(true);
					} else {
						if (log.isDebugEnabled()) {
							log.debug(debugPrefix + " - SqlSession of thread: "
									+ currentThread().getId() + " committing");
						}

						sqlSessionManager.commit(transactional.force());
					}
				} finally {
					if (log.isDebugEnabled()) {
						log.debug(format(
								"%s - SqlSession of thread: %s terminated its life-cycle, closing it",
								debugPrefix, currentThread().getId()));
					}

					sqlSessionManager.close();
				}
			} else if (log.isDebugEnabled()) {
				log.debug(format(
						"%s - SqlSession of thread: %s is inherited, skipped close operation",
						debugPrefix, currentThread().getId()));
			}
		}

		return object;
	}

	private Throwable convertThrowableIfNeeded(MethodInvocation invocation,
			DBTransactional transactional, Throwable t) {
		Method interceptedMethod = invocation.getMethod();

		// check the caught exception is declared in the invoked method
		for (Class<?> exceptionClass : interceptedMethod.getExceptionTypes()) {
			if (exceptionClass.isAssignableFrom(t.getClass())) {
				return t;
			}
		}

		// check the caught exception is of same rethrow type
		if (transactional.rethrowExceptionsAs().isAssignableFrom(t.getClass())) {
			return t;
		}

		// rethrow the exception as new exception
		String errorMessage;
		Object[] initargs;
		Class<?>[] initargsType;

		if (transactional.exceptionMessage().length() != 0) {
			errorMessage = format(transactional.exceptionMessage(),
					invocation.getArguments());
			initargs = new Object[] { errorMessage, t };
			initargsType = MESSAGE_CAUSE_TYPES;
		} else {
			initargs = new Object[] { t };
			initargsType = CAUSE_TYPES;
		}

		Constructor<? extends Throwable> exceptionConstructor = getMatchingConstructor(
				transactional.rethrowExceptionsAs(), initargsType);
		Throwable rethrowEx = null;
		if (exceptionConstructor != null) {
			try {
				rethrowEx = exceptionConstructor.newInstance(initargs);
			} catch (Exception e) {
				errorMessage = format(
						"Impossible to re-throw '%s', it needs the constructor with %s argument(s).",
						transactional.rethrowExceptionsAs().getName(),
						Arrays.toString(initargsType));
				log.error(errorMessage, e);
				rethrowEx = new RuntimeException(errorMessage, e);
			}
		} else {
			errorMessage = format(
					"Impossible to re-throw '%s', it needs the constructor with %s or %s argument(s).",
					transactional.rethrowExceptionsAs().getName(),
					Arrays.toString(CAUSE_TYPES),
					Arrays.toString(MESSAGE_CAUSE_TYPES));
			log.error(errorMessage);
			rethrowEx = new RuntimeException(errorMessage);
		}

		return rethrowEx;
	}

	@SuppressWarnings("unchecked")
	private static <E extends Throwable> Constructor<E> getMatchingConstructor(
			Class<E> type, Class<?>[] argumentsType) {
		Class<? super E> currentType = type;
		while (Object.class != currentType) {
			for (Constructor<?> constructor : currentType.getConstructors()) {
				if (Arrays.equals(argumentsType,
						constructor.getParameterTypes())) {
					return (Constructor<E>) constructor;
				}
			}
			currentType = currentType.getSuperclass();
		}
		return null;
	}

}
