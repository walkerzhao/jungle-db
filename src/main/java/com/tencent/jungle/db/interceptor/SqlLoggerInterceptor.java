package com.tencent.jungle.db.interceptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tencent.jungle.util.SqlParser;
import com.tencent.jungle.util.SqlParser.SelectSql;

@Intercepts({
		@Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class }),
		@Signature(type = StatementHandler.class, method = "update", args = { Statement.class }),
		@Signature(type = Executor.class, method = "update", args = { MappedStatement.class, Object.class}),
		@Signature(type = StatementHandler.class, method = "query", args = {
				Statement.class, ResultHandler.class }) })
public class SqlLoggerInterceptor implements Interceptor {
	private final static Logger LOG=LoggerFactory.getLogger("SQL_LOG");
	private final static ObjectFactory DEFAULT_OBJECT_FACTORY = new DefaultObjectFactory();
	private final static ObjectWrapperFactory DEFAULT_OBJECT_WRAPPER_FACTORY = new DefaultObjectWrapperFactory();
	private final static ReflectorFactory DEFAULT_REFLECTOR_FACTORY = new DefaultReflectorFactory();
	private boolean logSql;
	private boolean updateSql;
	private boolean logSqlTimeCost;

	public Object intercept(Invocation invocation) throws Throwable {
		if(invocation.getTarget() instanceof Executor && updateSql){
			MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
	        Object parameter = invocation.getArgs()[1];
	        BoundSql boundSql = mappedStatement.getBoundSql(parameter);
	        Configuration configuration = mappedStatement.getConfiguration();
	        String updateSql = boundSql.getSql();
	        Executor exe = (Executor) invocation.getTarget();
	        Connection connection = exe.getTransaction().getConnection();
	        recordOldValue(updateSql, boundSql, connection, configuration, parameter);
	        return invocation.proceed();
		}else{
			final String methodName = invocation.getMethod().getName();
			if (logSql && methodName.equals("prepare")) {
				StatementHandler statementHandler = (StatementHandler) invocation
						.getTarget();
				MetaObject metaStatementHandler = MetaObject.forObject(
						statementHandler, DEFAULT_OBJECT_FACTORY,
						DEFAULT_OBJECT_WRAPPER_FACTORY,DEFAULT_REFLECTOR_FACTORY);
				// 分离代理对象链(由于目标类可能被多个拦截器拦截，从而形成多次代理，通过下面的两次循环
				// 可以分离出最原始的的目标类)
				while (metaStatementHandler.hasGetter("h")) {
					Object object = metaStatementHandler.getValue("h");
					metaStatementHandler = MetaObject.forObject(object,
							DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY,DEFAULT_REFLECTOR_FACTORY);
				}
				// 分离最后一个代理对象的目标类
				while (metaStatementHandler.hasGetter("target")) {
					Object object = metaStatementHandler.getValue("target");
					metaStatementHandler = MetaObject.forObject(object,
							DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY,DEFAULT_REFLECTOR_FACTORY);
				}
				Configuration configuration = (Configuration) metaStatementHandler
						.getValue("delegate.configuration");
				// MappedStatement mappedStatement = (MappedStatement)
				metaStatementHandler.getValue("delegate.mappedStatement");
				BoundSql boundSql = (BoundSql) metaStatementHandler
						.getValue("delegate.boundSql");
				Object parameterObject = boundSql.getParameterObject();
				String sql = boundSql.getSql();
				if (parameterObject == null) {
					LOG.info("++++++++++++++++++++catched a new sql:" + sql
							+ ",parameter is empty");
				} else {
					LOG.info("++++++++++++++++++++catched a new sql:"
							+ sql
							+ ",parameter is :"
							+ setParameters(boundSql, parameterObject,
									configuration));
					
				}
				// 将执行权交给下一个拦截器
				return invocation.proceed();

			} else if (logSqlTimeCost && methodName.equals("update")) {

				long startTime = System.currentTimeMillis();
				try {
					return invocation.proceed();
				} finally {
					LOG.info("update cost time:"
							+ (System.currentTimeMillis() - startTime));
				}

			} else if (logSqlTimeCost && methodName.equals("query")) {

				long startTime = System.currentTimeMillis();
				try {
					return invocation.proceed();
				} finally {
					LOG.info("query cost time:"
							+ (System.currentTimeMillis() - startTime));
				}

			} else {
				return invocation.proceed();
			}
		}
		
	}

	public List<Object> setParameters(BoundSql boundSql,
			Object parameterObject, Configuration config) throws SQLException {
		List<ParameterMapping> parameterMappings = boundSql
				.getParameterMappings();
		if (parameterMappings != null) {
			List<Object> l = new ArrayList<Object>(parameterMappings.size());
			for (int i = 0; i < parameterMappings.size(); i++) {
				ParameterMapping parameterMapping = parameterMappings.get(i);
				if (parameterMapping.getMode() != ParameterMode.OUT) {
					Object value;
					String propertyName = parameterMapping.getProperty();
					if (boundSql.hasAdditionalParameter(propertyName)) { // issue
																			// #448
																			// ask
																			// first
																			// for
																			// additional
																			// params
						value = boundSql.getAdditionalParameter(propertyName);
					} else if (parameterObject == null) {
						value = null;
					} else if (config.getTypeHandlerRegistry().hasTypeHandler(
							parameterObject.getClass())) {
						value = parameterObject;
					} else {
						MetaObject metaObject = config
								.newMetaObject(parameterObject);
						value = metaObject.getValue(propertyName);
					}
					l.add(value);
				}
			}
			return l;
		}
		return Collections.emptyList();
	}

	
	private StringBuilder setPreStatParams(PreparedStatement pStmt, BoundSql boundSql, Object parameterObject, Configuration configuration, int needParamSize) throws SQLException {
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        StringBuilder result = new StringBuilder();
        if (parameterMappings != null) {
            int paramsSize = parameterMappings.size();
            for (int i = 0, j = 0; i < paramsSize; i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    Object value = null;
                    String propertyName = parameterMapping.getProperty();
                    if (boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (parameterObject == null) {
                        value = null;
                    } else if (configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass())) {
                        value = parameterObject;
                    } else {
                        MetaObject metaObject = configuration.newMetaObject(parameterObject);
                        value = metaObject.getValue(propertyName);
                    }
                    TypeHandler typeHandler = parameterMapping.getTypeHandler();
                    JdbcType jdbcType = parameterMapping.getJdbcType();
                    if (value == null && jdbcType == null)
                        jdbcType = configuration.getJdbcTypeForNull();
                    if(i>=(paramsSize - needParamSize)){
                        typeHandler.setParameter(pStmt, ++j, value, jdbcType);
                    }else{
                        result.append("`").append(propertyName).append("`").append("=").append(value);
                        if(i<(paramsSize - needParamSize-1)){
                            result.append(",");
                        }
                    }
                }
            }
        }
        return result;
    }

    private void recordOldValue(String updateSql, BoundSql boundSql, Connection connection, Configuration configuration, Object parameter) {
        SelectSql select = SqlParser.updateParser(updateSql);
        if (select != null) {
            PreparedStatement pStmt = null;
            ResultSet rs = null;
            try {
                pStmt = connection.prepareStatement(select.sql);
                StringBuilder result = setPreStatParams(pStmt, boundSql, parameter, configuration, select.needParamSize);
                rs = pStmt.executeQuery();
                StringBuilder sb = new StringBuilder("update info! Old value ");
                if (rs.next()) {
                    sb.append("(");
                    for(int i=1;i<=select.columns.size();i++){
                        sb.append(select.columns.get(i-1)).append("=").append(rs.getObject(i));
                        if(i!=select.columns.size())
                            sb.append(",");
                    }
                    sb.append(")");
                }
                sb.append("##New value (").append(result).append(")");
               
                LOG.info(sb.toString());
                
            } catch (SQLException e) {
                LOG.error("recordOldValue {}",e);
            } finally {
                try {
                    rs.close();
                    pStmt.close();
                } catch (SQLException e) {
                	LOG.error("recordOldValue close {}",e);
                }

            }
        }

    }
	
	public Object plugin(Object target) {
		if (target instanceof StatementHandler || target instanceof Executor) {
			return Plugin.wrap(target, this);
		}
		return target;
	}

	public void setProperties(Properties properties) {
		this.logSql = Boolean.parseBoolean(properties.getProperty("logSql",
				"false"));
		this.updateSql = Boolean.parseBoolean(properties.getProperty("updateSql","false"));
		this.logSqlTimeCost = Boolean.parseBoolean(properties.getProperty(
				"logSqlTimeCost", "false"));
	}

}
