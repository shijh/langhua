package com.alipay.util;

import java.util.Date;
import java.util.Random;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
/**
 * Name：Custom orders category
 * Function：Tools can be used to obtain the system date, order number, etc.
 * Interface Name：Standard dual-interface kind
 * Version：2.0
 * Date：2008-12-25
 * Author：Squibb to pay sales technical support team
 * Phone：0571-26888888
 * Copyright：Alipay.com Co.,Ltd
 * */
public class UtilDate {
	public  static String getOrderNum(){
		Date date=new Date();
		DateFormat df=new SimpleDateFormat("yyyyMMddHHmmss");
		return df.format(date);
	}
	//Acquisition date, format：yyyy-MM-dd HH:mm:ss
	public  static String getDateFormatter(){
		Date date=new Date();
		DateFormat df=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return df.format(date);
	}
	
	
	public static String getDate(){
		Date date=new Date();
		DateFormat df=new SimpleDateFormat("yyyyMMdd");
		return df.format(date);
	}
	
	//Have a random three-digit
	public static String getThree(){
		Random rad=new Random();
		return rad.nextInt(1000)+"";
	}
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		UtilDate date=new UtilDate();
		System.out.println(date.getOrderNum());
		System.out.println(date.getDateFormatter());
		System.out.println(date.getThree());
	}
	
}
