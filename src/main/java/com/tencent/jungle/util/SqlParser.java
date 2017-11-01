package com.tencent.jungle.util;

import java.util.ArrayList;
import java.util.List;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.update.Update;

public class SqlParser {
	public static class SelectSql{
		public String sql;
		public int needParamSize;
		public List<String> columns;
		public List<String> tables;
        public SelectSql(String sql, int needParamSize,List<String> columns,List<String> tables ) {
            super();
            this.sql = sql;
            this.needParamSize = needParamSize;
            this.columns = columns;
            this.tables = tables;
        }
		
	}
	public static int occurTimes(String string, String a) {
		if(string==null||a==null)
			return 0;
		int pos = -2;
		int n = 0;
		while (pos != -1) {
			if (pos == -2) {
				pos = -1;
			}
			pos = string.indexOf(a, pos + 1);
			if (pos != -1) {
				n++;
			}
		}
		return n;
	}
	
	

	public static SelectSql updateParser(String sql){
		
		try {
			Statement statement = CCJSqlParserUtil.parse(sql);
			Update updateStatement = (Update) statement;
			List<Table> tables = updateStatement.getTables();
			List<Column> columns = updateStatement.getColumns();
			Expression expression = updateStatement.getWhere();
			
			List<String> columnNames = new ArrayList<String>();
			List<String> tableNames = new ArrayList<String>();
			StringBuilder selectSql = new StringBuilder("select ");
			for(int i=0;i<columns.size();i++){
				selectSql.append(columns.get(i).toString());
				columnNames.add(columns.get(i).toString());
				if(i==columns.size()-1){
					selectSql.append(" from ");
					break;
				}else{
					selectSql.append(",");
				}
			}
			
			for(int i=0;i<tables.size();){
				selectSql.append(tables.get(i).toString());
				tableNames.add(tables.get(i).toString());
				if(i==tables.size()-1){
					break;
				}else{
					selectSql.append(",");
				}
			}
			int needParamSize = 0;
			if(expression!=null){
				String con = expression.toString();
				needParamSize = occurTimes(con, " ?");
				
				selectSql.append(" where ").append(con);
			}
			SelectSql select = new SelectSql(selectSql.toString(), needParamSize,columnNames,tableNames);
			return select;
		} catch (JSQLParserException e) {
			e.printStackTrace();
		}

		return null;
	}
	
	
	public static void main(String[] args) {
		List<Object> list = new ArrayList<Object>();
		list.add(12);
		list.add(13);
		list.add(14);
		System.out.println(updateParser("update info set `detail`=?,`id`=?  WHERE `id`=? or `id`=?"));
	}
	
	
}
