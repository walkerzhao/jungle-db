package com.tencent.jungle.db;

import static com.google.inject.matcher.Matchers.annotatedWith;
import static com.google.inject.matcher.Matchers.any;

import java.util.Iterator;
import java.util.Properties;

import org.apache.ibatis.session.SqlSession;
import org.mybatis.guice.XMLMyBatisModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.tencent.jungle.db.datasource.DataSourceConfigBuilder;
import com.tencent.jungle.db.datasource.DataSources;
import com.tencent.jungle.db.datasource.DynamicDataSources;
import com.tencent.jungle.db.datasource.SqlSessionProvider;
import com.tencent.jungle.db.transaction.DBTransactional;
import com.tencent.jungle.db.transaction.TransactionalMethodInterceptor;
import com.tencent.jungle.util.JungleConfiguration;

public class JungleDatabaseModule extends XMLMyBatisModule {
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	@Inject
	private JungleConfiguration jungleConfig ;
	public JungleDatabaseModule(){
	}
	@Override
	protected void initialize() {
		// read conf from jungle conf
		Properties properties = new Properties();
		Iterator<String> it = jungleConfig.getKeys();
//		while(it.hasNext()) {
//			String key = it.next();
//			System.out.println(key);
//		}
//		it = jungleConfig.getKeys();
		while(it.hasNext()) {
			String key = it.next();
			String value = jungleConfig.getString(key);
			if(value != null) {
				properties.setProperty(key, value);
			}
		}
		addProperties(properties);
		//
		String environmentId = jungleConfig.getString("jungle.db.default.environment", "default");
		setEnvironmentId(environmentId);
		setClassPathResource("META-INF/jungle-mybatis.xml");

		DataSourceConfigBuilder builder = null;
		try {
			builder = new DataSourceConfigBuilder();
		} catch (Exception e) {
			logger.error("multi-datasource may be not config", e);
		}
		if (null != builder && builder.allIds()!=null) {
			// requestInjection(builder);
			bind(DataSourceConfigBuilder.class);
			bind(DynamicDataSources.class);
			for (String string : builder.allIds()) {
				SqlSessionProvider provider = new SqlSessionProvider(string);
				requestInjection(provider);
				bind(SqlSessionProvider.class).annotatedWith(
						Names.named(string)).toInstance(provider);
				bind(SqlSession.class).annotatedWith(Names.named(string))
						.toProvider(
								(Key.get(SqlSessionProvider.class,
										Names.named(string))));
			}
		}
		bind(DataSources.class);
		// transactional interceptor
		TransactionalMethodInterceptor interceptor = new TransactionalMethodInterceptor();
		requestInjection(interceptor);
		bindInterceptor(any(), annotatedWith(DBTransactional.class),
				interceptor);

	}

}
