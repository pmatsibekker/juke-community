package org.juke.remix.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import org.juke.framework.config.ConfigUtil;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

public class RemixUtil {
	public static final String PATTERN = "^[\\w\\-]+[\\\\w\\\\-]*$";
	public static final String ZIP =".zip";
	public static final String OK="OK";
	public static final String NOK="NOK";
	public static boolean validatgeName(String name) {
		if (name == null)
			return false;
		 
		return name.matches(PATTERN);
	}
	
	public static String getWhiteList(String test) {
		String testZip = test.trim() + ZIP;
		String jukeDir= ConfigUtil.getJukePath();
		File f= new File(jukeDir);
		if (!f.exists())
			return null;
		
		String[] testArray = f.list();
		int index = Arrays.asList(testArray).indexOf(testZip);
		if (index <0)
			return null;
		
		return testArray[index].substring(0,testArray[index].length()- ZIP.length());
	}
	

	
	public static void write(String path, HttpServletResponse response) throws FileNotFoundException, IOException {
		File f= new File(path);
		try(FileInputStream fis = new FileInputStream(f)){
			IOUtils.copy(fis, response.getOutputStream());
			response.flushBuffer();
		}
	}
}
