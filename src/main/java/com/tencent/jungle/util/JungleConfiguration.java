package com.tencent.jungle.util;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.EnvironmentConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SubsetConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Jungle的配置。配置从若干个地方获取，其优先次序为：
 */
public class JungleConfiguration extends CompositeConfiguration {

	final Logger logger = LoggerFactory.getLogger(JungleConfiguration.class);

	Object servletContext = null;

	public final String appname;
	
	private JungleConfiguration() {
		super();
		setThrowExceptionOnMissing(false);
		appname = init();
	}
	
	private final static JungleConfiguration jungleConfigs = new JungleConfiguration();
	public static JungleConfiguration getInstance(){
		return jungleConfigs;
	}
	
	String init() {
		log("create configuration");

		String homeDir = System.getenv("HOME");
		String appName = getAppName();
		log("Jungle app name is " + appName);

		// 环境变量最优先： 次序为<basename>前缀，jungle前缀，无前缀
		EnvironmentConfiguration envconf = new MyEnvironmentConfiguration();

		if (appName != null) {
			addConfiguration(new SubsetConfiguration(envconf, appName));
			log("add env config subset[" + appName + "]");
		}
		addConfiguration(new SubsetConfiguration(envconf, "jungle"));
		log("add env config subset[jungle]");
		addConfiguration(envconf);
		log("add env config");

		// 文件系统中，次序为
		// ../conf/jungle.properties
		// ${JUNGLE_SPEC_CONF}/jungle.properties
		// $HOME/.<basename>.properties
		// /etc/jungle/<basename>.properties
		// $HOME/.jungle.properties
		// /etc/jungle/jungle.properties
		log("user.dir is" + System.getProperty("user.dir"));
		addConfigURL("../conf/jungle.properties");
		//
		String specConf = System.getProperty("JUNGLE_SPEC_CONF");
		log("JUNGLE_SPEC_CONF path: " + specConf);
		addConfigURL(specConf + "/jungle.properties");

		if (appName != null) {
			addConfigURL(homeDir + "/." + appName + ".properties");
			addConfigURL("/etc/jungle/" + appName + ".properties");
		}
		addConfigURL(homeDir + "/.jungle.properties");
		addConfigURL("/etc/jungle/jungle.properties");

		// 系统属性
		addConfiguration(new SystemConfiguration());
		log("add system config");

		// 然后是 WEB-INF/ 下的文件
		if (appName != null)
			addConfigURL("webapp:" + appName + ".properties");
		addConfigURL("webapp:jungle.properties");

		// 最后是classpath下的文件
		if (appName != null)
			addConfigURL("classpath:" + appName + ".properties");
		addConfigURL("classpath:jungle.properties");

		return appName;
	}

	void log(String msg) {
		/*
		 * if (servletContext != null)
		 * servletContext.log("JungleConfiguration: " + msg); else
		 * System.out.println("JungleConfiguration: " + msg);
		 */
		// System.out.println("JungleConfiguration: " + msg);
		logger.info(msg);
	}

	void addConfigURL(String spec) {
		URL url;
		try {
			url = newURL(spec);
		} catch (MalformedURLException e) {
			url = null;
		}
		if (url == null)
			return;

		try {
			PropertiesConfiguration config = new PropertiesConfiguration(url);
			config.setEncoding("utf8");
			this.addConfiguration(config);
			log("add url config " + url.toString());
		} catch (ConfigurationException e) {
			// ignore
		}
	}

	private URL newURL(String spec) throws MalformedURLException {
		if (spec.startsWith("classpath:"))
			return getClass().getClassLoader().getResource(spec.substring(10));

		if (spec.startsWith("file:"))
			spec = spec.substring(5);

		File file = new File(spec);
		if (file.exists() && file.isFile() && file.canRead())
			return file.toURI().toURL();
		return null;
	}

	private String getAppName() {
		String appName = null;
		if (appName == null)
			appName = System.getenv("JUNGLE_APP_NAME");
		if (appName == null)
			appName = System.getProperty("JUNGLE_APP_NAME");
		if ("jungle".equals(appName)) // jungle名总是要加载
			appName = null;
		return appName;
	}

	class MyEnvironmentConfiguration extends EnvironmentConfiguration {

		private String normalize(String key) {
			return key.toUpperCase().replaceAll("[\\.\\-]", "_");
		}

		@Override
		public boolean containsKey(String key) {
			return super.containsKey(normalize(key)); // To change body of
														// overridden methods
														// use File | Settings |
														// File Templates.
		}

		@Override
		public Object getProperty(String key) {
			return super.getProperty(normalize(key)); // To change body of
														// overridden methods
														// use File | Settings |
														// File Templates.
		}

	}
}
