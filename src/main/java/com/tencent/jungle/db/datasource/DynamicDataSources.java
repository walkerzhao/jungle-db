package com.tencent.jungle.db.datasource;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
@Singleton
public class DynamicDataSources {
	protected final Logger LOG = LoggerFactory.getLogger(getClass());
	private Map<String, DataSource> datasources = new ConcurrentHashMap<String, DataSource>();;
	private Configuration config;
	@Inject
	public DynamicDataSources(SqlSessionManager manager){
		this.config = manager.getConfiguration();
	}
	
	public DataSource getDataSource(String key){
		return datasources.get(key);
	}
	
	/**
	 * 注意！非HikariDataSource，移除后需要手动释放datasource资源
	 * @param key
	 * @return
	 */
	public DataSource removeDataSource(String key){
		DataSource ds = datasources.remove(key);
		if(ds!=null && ds instanceof HikariDataSource){
			((HikariDataSource)ds).shutdown();
		}
		return ds;
	}
	/**
	 * 注意！非HikariDataSource，更新后需要手动释放datasource资源
	 * @param key
	 * @return
	 */
	public DataSource updateDataSource(String key,String ds_type,Map<String,Object> config) throws Exception {
		synchronized (key.intern()) {
			DataSource ds = removeDataSource(key);
			addDataSource(key, ds_type, config);
			return ds;
		}
	}
	
	public void addDataSource(String key,String ds_type,Map<String,Object> config) throws Exception {
		Properties props = new Properties();
		for(Map.Entry<String,Object> prop:config.entrySet()){
			props.put(prop.getKey(), prop.getValue());
		}
		DataSourceFactory dsFactory = dataSourceElement(ds_type,props);
		DataSource dataSource = dsFactory.getDataSource();
		datasources.put(key, dataSource);
	}

	private DataSourceFactory dataSourceElement(String type,Properties props) throws Exception {
			DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
			factory.setProperties(props);
			return factory;
	}

	private Class<?> resolveClass(String type) {
		return config.getTypeAliasRegistry().resolveAlias(type);
	}
	
}
