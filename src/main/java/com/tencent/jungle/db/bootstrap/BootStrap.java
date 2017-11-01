package com.tencent.jungle.db.bootstrap;

import java.util.ArrayList;
import java.util.List;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.tencent.jungle.db.JungleDatabaseModule;

public class BootStrap {
	public static void main(String[] args) {
		System.out.println("hello,world");
		List<Module> modules = loadModule();
		Injector inject = Guice.createInjector(modules);
		
		//TODO--使用jungle db 去访问db
	}
	
	private static List<Module> loadModule() {
		List<Module> modules = new ArrayList<Module>();
		modules.add(new JungleDatabaseModule());
		return modules;
	}
	
	
	//TODO--新建一个mapper去访问本地db
}
