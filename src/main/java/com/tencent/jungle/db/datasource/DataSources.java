package com.tencent.jungle.db.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.ibatis.session.TransactionIsolationLevel;

import com.google.inject.Inject;
import com.google.inject.Singleton;
@Singleton
public class DataSources {
	private  Map<String, DataSource> sources;
	private SqlSessionManager manager;

	public  DataSource get(String name) {
		return sources.get(name);
	}
	
	@Inject
	public DataSources(DataSourceConfigBuilder builder,SqlSessionManager manager) {
		super();
		this.sources = builder.build();
		this.manager=manager;
	}
	
	public SqlSession openNamedSession(String name){
		try {
			return manager.openSession(sources.get(name).getConnection());
		} catch (SQLException e) {
		}
		return null;
	}

	public boolean isManagedSessionStarted() {
		return manager.isManagedSessionStarted();
	}

	public void startManagedSession(ExecutorType executorType,
			TransactionIsolationLevel transactionIsolationLevel) {
		manager.startManagedSession(executorType, transactionIsolationLevel);
		
	}

	public void rollback(boolean b) {
		manager.rollback(b);
		
	}

	public void commit(boolean force) {
		manager.commit(force);
		
	}

	public void close() {
		manager.close();
		
	}

	public void startManagedSession(ExecutorType executorType, Connection con) {
		manager.startManagedSession(executorType, con);
		
	}

}
