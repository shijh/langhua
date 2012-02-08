/* This library is part of Taglib OpenCms module of Langhua
 *
 * Copyright (C) 2008 Langhua Opensource (http://www.langhua.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org 
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.opencms.misc;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.file.CmsResourceFilter;
import org.opencms.jsp.CmsJspXmlContentBean;
import org.opencms.jsp.I_CmsXmlContentContainer;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsStringUtil;

public class DataFormat {
	private static Date nowTime;
	private static String detestyle = "yyyy-MM-dd hh:mm:ss";
	private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
			getDetestyle());
	private static DataFormat dataFormat = null;
	private static final long TWELVEHOURS = 43200000l;
	private static final long TWENTYFOURHOURS = 86400000l;
	private static final long FOURTYEIGHTH = 172800000l;
	private static final long THREEDAYS = 259200000l;
	private static final long ONEWEEK = 604800000l;
	private static final long ONEMONTH = 2592000000l;
	private static final long THREEMONTH = 7776000000l;
	private static final long SIXMONTH = 15552000000l;
	private static final long ONEYEAR = 31104000000l;
	private static final long TWOYEARS = 62208000000l;
	private static final long THREEYEARS = 93312000000l;
	private static final String QING = "��";	
	private static final String LIGHT_RAIN = "С��";	
	private static final String HAZE = "����";	
	private static final String SMOKE = "��";	
	private static final String MIST = "����";	
	private static final String FOG = "����";	
	private static final String MIST_SMOKE = "����";	
	private static final String LIGHT_FREEZING_DRIZZLE = "ϸ��";	
	private static final String LIGHT_RAIN_MIST = "С�걡��";
	private static final String LIGHT_RAIN_FOG = "С�꣬��";
	private static final String SMOKE_LIGHT_RAIN = "����,С��";

	private DataFormat() {

	}

	public static synchronized DataFormat getInstance() {
		if (dataFormat == null) {
			dataFormat = new DataFormat();
			return dataFormat;
		}
		return null;
	}

	public static boolean compare(String fileName) {
		if (fileName.toUpperCase().equals(fileName)
				&& fileName.toLowerCase().equals(fileName)) {
			return true;
		} else {
			return false;
		}
	}

	public static String getDate(CmsResource cmsResource) {
		String resultString = cmsResource.toString();
		resultString = resultString.substring(resultString
				.indexOf("date created") + 18, resultString
				.indexOf("date created") + 42);
		Date date = new Date(resultString);
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
				"yyyy-MM-dd HH:mm:ss");
		return simpleDateFormat.format(date);
	}

	public static String getDate(long datetime) {
		Date date = new Date(datetime);
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
				"yyyy-MM-dd HH:mm:ss");
		return simpleDateFormat.format(date);
	}
	
	public static String getDate(long datetime,String formatmodel) {
		Date date = new Date(datetime);
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(formatmodel);
		return simpleDateFormat.format(date);
	}
	

	public static long replace(String datetime) {
		if (datetime.equals("12Сʱ")) {
			return TWELVEHOURS;
		} else if (datetime.equals("24Сʱ")) {
			return TWENTYFOURHOURS;
		}else if(datetime.equals("48Сʱ")){
			return FOURTYEIGHTH;
	    }else if (datetime.equals("����")) {
			return THREEDAYS;
		} else if (datetime.equals("һ������")) {
			return ONEWEEK;
		} else if (datetime.equals("һ����")) {
			return ONEMONTH;
		} else if (datetime.equals("�����")) {
			return THREEMONTH;
		} else if (datetime.equals("�����")) {
			return SIXMONTH;
		} else if (datetime.equals("һ��")) {
			return ONEYEAR;
		} else if (datetime.equals("����")) {
			return TWOYEARS;
		} else if (datetime.equals("����")) {
			return THREEYEARS;
		} else if (datetime.equals("������")) {
			Date date = new Date();
			return date.getTime();
		}

		return TWENTYFOURHOURS;
	}

	public static Boolean compareTime(long createDateTime, String restrictionsTime) {
		Calendar calendar = Calendar.getInstance();
		long nowTime = calendar.getTimeInMillis();
		long createToNowTime = nowTime - createDateTime;		
		if (createToNowTime < DataFormat.replace(restrictionsTime)) {
			return true;
		}
		return false;
	
	}

	public static String formatFileName(String tagWidthString, String fileName,
			String fontSizeString, int length) {
		int fontSize = 0;
		int tagWidth = 0;		
		try {
			fontSize = Integer.parseInt(fontSizeString);
			tagWidth = Integer.parseInt(tagWidthString);
			int fileLNum = fileName.length();
			int fileLength = fileLNum * fontSize;
			int fileLine = tagWidth - 40 - length * fontSize;
			if (DataFormat.compare(fileName)) {
				if (fileLine < fileLength) {
					int numm = fileLine / fontSize-2;
					fileName = fileName.substring(0, numm) + "...";
				}
			} else {
				if (fileLine < fileLength * 2) {
					int numm = fileLine / fontSize-2;
					fileName = fileName.substring(0, numm * 2) + "...";
				}
			}
		} catch (Exception e) {
			
			e.printStackTrace();
		}
		return fileName;
	}
	/*
	 * 
	 */
	public static String formatFileName(String fileName, String tagWidth,int availabilityLength, String fontSize) {
		int tagWi = 0;
		int fontSi = 0;	
		try {
			tagWi = Integer.parseInt(tagWidth);
			fontSi = Integer.parseInt(fontSize);
		} catch (Exception e) {
			e.printStackTrace();
		}
		int fileLNum = fileName.length();
		int fileLength = fileLNum * fontSi;
		int fileLine = tagWi - availabilityLength;
		if (DataFormat.compare(fileName)) {
			if (fileLine < fileLength) {
				int numm = fileLine / fontSi;
				fileName = fileName.substring(0, numm) + "...";
			}else{
				return fileName;
			}
		} else {
			if (fileLine < fileLength * 2) {
				int numm = fileLine / fontSi;
				fileName = fileName.substring(0, numm * 2) + "...";
			}else{
				return fileName;
			}
		}

		return fileName;
	}
	public static String formatFileName(int tagWidth, String fileName,
			String fontSizeString, int length) {
		int fontSize = 0;
		try {
			fontSize = Integer.parseInt(fontSizeString);
			int fileLNum = fileName.length();
			int fileLength = fileLNum * fontSize;
			int fileLine = tagWidth / 2 - 40 - length * fontSize;
			if (DataFormat.compare(fileName)) {
				if (fileLine < fileLength) {
					int numm = fileLine / fontSize;
					fileName = fileName.substring(0, numm) + "...";
				}else{
					return fileName;
				}
			} else {
				if (fileLine < fileLength * 2) {
					int numm = fileLine / fontSize;
					fileName = fileName.substring(0, numm * 2) + "...";
				}else{
					return fileName;
				}
			}
		} catch (Exception e) {
		
			e.printStackTrace();
		}
		return fileName;
	}

	public static void main(String[] args) {
		
	}
	/*
	 * Formatted file name 
	 */
	public static String formatFileName(String fileName,
			int displayChineseChar, int displayCodeChar) {
		if (DataFormat.compare(fileName)) {
			if (fileName.length() > displayChineseChar) {
				return fileName.substring(0, displayChineseChar) + "...";
			}
		} else {
			if (fileName.length() > displayCodeChar) {
				return fileName.substring(0, displayCodeChar) + "...";
			}
		}
		return fileName;
	}

	/*
	 * Formatted path, from the absolute path
	 */
	public static String formatpath(String path) {
		String path2 = path.substring(6, path.length());
		String addressName = path2.substring(1, path2.indexOf("/", 2));
		int le = 7 + addressName.length();
		String realpath = path.substring(le, path.length());
		return realpath;
	}

	/*
	 * Determine whether the path is a designated section of the article
	 */
	public static Boolean isXinPath(String path, String columsName) {
		if(columsName.equals("/")){
			return true;
		}
		if (path.startsWith(columsName)) {			
			return true;
		}
		return false;
	}
	public static Boolean isPath(String path, String columsName) {
		if(columsName.equals("/")){
			return true;
		}
		if (columsName.contains("/")) {
			
			int length = path.indexOf("/", 2)
					+ (path.substring(path.indexOf("/", 2), path.length()))
							.indexOf("/", 2);
			String path2 = path.substring(length, path.length());
			int length2 = path2.indexOf("/", 2)
					+ (path2.substring(path.indexOf("/", 2), path2.length()))
							.indexOf("/", 2) + 1;
			String addressName = path2.substring(1, length2);
			if (addressName.equals(columsName)) {
				return true;
			} else {
				return false;
			}
		} else {
			int length = path.indexOf("/", 2)
					+ (path.substring(path.indexOf("/", 2), path.length()))
							.indexOf("/", 2);
			String path2 = path.substring(length, path.length());
			String addressName = path2.substring(1, path2.indexOf("/", 2));
			if (addressName.equals(columsName)) {
				return true;
			} else {
				return false;
			}
		}

	}

	public static String getNowTime() {
		nowTime = new Date();
		return simpleDateFormat.format(nowTime);
	}

	public int getYear() {
		return nowTime.getYear() + 1900;
	}

	public static String getDetestyle() {
		return detestyle;
	}

	public void setDetestyle(String detestyle) {
		this.detestyle = detestyle;
	}
	
	public static String getVisibility(String visibility){
		if(CmsStringUtil.isEmpty(visibility)){
			return "---";
		}
		String visi = visibility.substring((visibility.indexOf("mile")-2), visibility.indexOf("mile")-1);
		Double visiDouble = 0.0;
		String str = "";
		try{
			visiDouble = Double.parseDouble(visi)*1.610;			
			str = String.valueOf(visiDouble);
			int leng = str.indexOf(".")+3;		
			str = str.substring(0, leng);
		}catch (Exception e) {
			e.printStackTrace();
		}
		if(visibility.contains("greater than")){
			visi = "����"+str;
		}else if (visibility.contains("less than")) {
			visi = "С��"+str;
		}		
		return visi;
	}

	public static String getSkyStatus(String skystatus,CmsObject cmsObject){		
		new CmsWeatherDescription(cmsObject);	
		if(CmsStringUtil.isEmpty(skystatus)){
			return  QING;
		}else {
			skystatus = skystatus.substring(0, skystatus.trim().length());
			if(CmsStringUtil.isEmpty(CmsWeatherDescription.mapDesc.get(skystatus))){
				return skystatus;
			}
		}
		return CmsWeatherDescription.mapDesc.get(skystatus);
	}	


}
