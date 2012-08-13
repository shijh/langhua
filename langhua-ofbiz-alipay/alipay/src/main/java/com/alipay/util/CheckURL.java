  
package com.alipay.util;
import java.net.*;
import java.io.*;

/**
 * Name：To verify payment type
 * Function：Responsible to verify relevant information and return to pay back to the results of Bao ATN
 * Interface Name：Standard dual-interface kind
 * Version：2.0
 * Date：2008-12-25
 * Author：Squibb to pay sales technical support team
 * Phone：0571-26888888
 * Copyright：Alipay.com Co.,Ltd
 * */
public class CheckURL {
	/**
     * 	MD5 encryption of the string
	 * @param myUrl 
     *
     * @param url
     *
     * @return Access to the content url
     */
  public static String check(String urlvalue ) {
	 
	 
	  String inputLine="";
	  
		try{
				URL url = new URL(urlvalue);
				
				HttpURLConnection urlConnection  = (HttpURLConnection)url.openConnection();
				
				BufferedReader in  = new BufferedReader(
			            new InputStreamReader(
			            		urlConnection.getInputStream()));
			
				inputLine = in.readLine().toString();
			}catch(Exception e){
				e.printStackTrace();
			}
			
			
	    return inputLine;
  }


  }