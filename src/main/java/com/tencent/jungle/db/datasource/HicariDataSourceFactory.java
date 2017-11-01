package com.tencent.jungle.db.datasource;

import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.DataSourceFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class HicariDataSourceFactory implements DataSourceFactory{
    private DataSource datasource;
	public void setProperties(Properties props) {
		HikariConfig config=new HikariConfig(props);
		this.datasource=new HikariDataSource(config);
	}

	public DataSource getDataSource() {
		return datasource;
	}

}
