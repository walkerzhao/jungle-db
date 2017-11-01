package com.tencent.jungle.db.datasource;

import org.apache.ibatis.session.SqlSession;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class SqlSessionProvider implements Provider<SqlSession> {
    private DataSources datasources;
    private String name;
    
	public SqlSessionProvider(String name) {
		super();
		this.name = name;
	}
	@Inject
	public void onInit(DataSources datasources){
    	this.datasources=datasources;
    }
	public SqlSession get() {
		return datasources.openNamedSession(name);
	}

}
