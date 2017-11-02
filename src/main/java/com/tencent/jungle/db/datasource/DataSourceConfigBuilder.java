package com.tencent.jungle.db.datasource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.tencent.jungle.util.JungleConfiguration;

public class DataSourceConfigBuilder {
	protected final Logger LOG = LoggerFactory.getLogger(getClass());
	private Map<String, DataSource> datasources;
	private XPathParser parser;
	private Properties variables;
	private Configuration config;
	private HashSet<String> ids;
	

	private JungleConfiguration jungleConfig;

	@Inject
	public DataSourceConfigBuilder(SqlSessionManager manager, Injector injector) {
		this();
		if(this.parser!=null){
			onConfig(manager);
		}
	}

	public DataSourceConfigBuilder() {
		super();
		jungleConfig = JungleConfiguration.getInstance();
		try {
			this.parser = createParser();
			allSources(parser.evalNode("configs"));
		} catch (Exception e) {
			LOG.error("Exception while parse datasource-config.xml", e);
		}
	}

	protected XPathParser createParser() throws IOException {
		InputStream is = null;
		String xml = null;
		File xfile = new File("../conf/datasource-config.xml");
		if(xfile.exists() && xfile.canRead()) {
			xml = xfile.getAbsolutePath();
			is = new FileInputStream(xfile);
		} else {
			xml = "META-INF/datasource-config.xml";
			String dbtest = jungleConfig.getString("jungle.db.test", "false").trim().toLowerCase();
			if("false".equalsIgnoreCase(dbtest)) {
				xml = "META-INF/datasource-config.xml";
			} else if("true".equalsIgnoreCase(dbtest)) {
				xml = "META-INF/datasource-config-test.xml";
			} else {
				xml = "META-INF/datasource-config-"+dbtest+".xml";
			}
//			if (jungleConfig.getBoolean("jungle.db.test", false)) {
//				xml = "META-INF/datasource-config-test.xml";
//			}
			is = Resources.getResourceAsStream(xml);
		}
		LOG.error("DataSourceConfigBuilder createParser with: {}", xml);
		return new XPathParser(is);
	}

	// @Inject
	private void onConfig(SqlSessionManager manager) {
		this.config = manager.getConfiguration();
		XNode configs = parser.evalNode("configs");
		Iterator<String> it = jungleConfig.getKeys();
		while (it.hasNext()) {
			String key = (String) it.next();
			if (key.startsWith("jungle.db.")) {
				if (variables == null) {
					variables = new Properties();
				}
				if (jungleConfig.getProperty(key) != null)
					variables.put(key, jungleConfig.getProperty(key));
			}
		}
		// XNode properties = configs.evalNode("properties");
		/*
		 * if(properties!=null){ try { propertiesElement(properties); } catch
		 * (Exception e) {
		 * LOG.error("propertiesElement while parse node configs", e); } }
		 */

		try {
			environmentsElement(configs);
		} catch (Exception e) {
			LOG.error("Exception while parse node configs", e);
		}
	}

	public Set<String> allIds() {
		return ids;
	}

	private void allSources(XNode context) throws Exception {
		if (context != null) {
			HashSet<String> ids = new HashSet<String>();
			for (XNode child : context.getChildren()) {
				if (child.getName().equals("environment")) {
					String id = child.getStringAttribute("id");
					ids.add(id);
				}
			}
			this.ids = ids;
		}
	}

	private void environmentsElement(XNode context) throws Exception {
		if (context != null) {
			for (XNode child : context.getChildren()) {
				if (child.getName().equals("environment")) {
					String id = child.getStringAttribute("id");
					DataSourceFactory dsFactory = dataSourceElement(child
							.evalNode("dataSource"));
					DataSource dataSource = dsFactory.getDataSource();
					Map<String, DataSource> datasources = this.datasources;
					if (null == this.datasources) {
						datasources = this.datasources = new HashMap<String, DataSource>();
					}
					datasources.put(id, dataSource);
				}
			}
		}
	}

	private DataSourceFactory dataSourceElement(XNode context) throws Exception {
		if (context != null) {
			String type = context.getStringAttribute("type");
			Properties props = context.getChildrenAsProperties();
			if (this.variables != null && this.variables.size() > 0) {
				Iterator it = props.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry entry = (Map.Entry) it.next();
					String value = (String) entry.getValue();
					entry.setValue(PropertyParser.parse(value, this.variables));
				}
			}
			DataSourceFactory factory = (DataSourceFactory) resolveClass(type)
					.newInstance();
			factory.setProperties(props);
			return factory;
		}
		throw new BuilderException(
				"Environment declaration requires a DataSourceFactory.");
	}

	private Class<?> resolveClass(String type) {
		return config.getTypeAliasRegistry().resolveAlias(type);
	}

	public Map<String, DataSource> build() {
		return datasources;
	}

	private void propertiesElement(XNode context) throws Exception {
		if (context != null) {
			Properties defaults = context.getChildrenAsProperties();
			String resource = context.getStringAttribute("resource");
			String url = context.getStringAttribute("url");
			if (resource != null && url != null) {
				throw new BuilderException(
						"The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
			}
			if (resource != null) {
				defaults.putAll(Resources.getResourceAsProperties(resource));
			} else if (url != null) {
				defaults.putAll(Resources.getUrlAsProperties(url));
			}
			variables = defaults;
		}
	}
}
