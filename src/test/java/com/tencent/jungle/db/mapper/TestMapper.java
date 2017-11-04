package com.tencent.jungle.db.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.mybatis.guice.transactional.Isolation;
import org.mybatis.guice.transactional.Transactional;

public interface TestMapper {
	@Results({ @Result(id = true, column = "id", property = "id_map"),
			@Result(column = "name", property = "name_map") })
	@Select("select * from blog where 1=1 order by id")
	@Options(useCache = false, fetchSize = 20, flushCache = true)
	List<Map<String, Object>> getUser(Map<String, Object> m);

	@Insert("insert into blog (name,author) values (#{name}, #{author})")
	@Options(useGeneratedKeys = true, keyProperty="id")
	void insert(Map<String, Object> m);
	
//	@Results(@Result(property="id"))
//	@Insert("insert into t_user (name) values (#{name}) ")
//	@Options(useGeneratedKeys = true, keyProperty="id")
//	void insert1(T m);
	
	@Results({ @Result(id = true, column = "id", property = "id",javaType=Long.class)})
	@Select("select id from t_user where name=#{name}")
	@Options(useGeneratedKeys = true, keyProperty="id")
	long select(@Param("name")String name);
}
