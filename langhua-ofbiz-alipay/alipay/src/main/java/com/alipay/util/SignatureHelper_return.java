package com.alipay.util;

import java.io.FileWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
/**
 * Name：To pay the return type to deal with simultaneous access to help category
 * Function：Supporting simultaneous return for authentication, such as writing log action
 * Interface Name：Standard dual-interface kind
 * Version：2.0
 * Date：2008-12-25
 * Author：Squibb to pay sales technical support team
 * Phone：0571-26888888
 * Copyright：Alipay.com Co.,Ltd
 * */
public class SignatureHelper_return {
	public static String sign(Map params, String privateKey) {
		Properties properties = new Properties();

		for (Iterator iter = params.keySet().iterator(); iter.hasNext();) {
			String name = (String) iter.next();
			Object value = params.get(name);

			if (name == null || name.equalsIgnoreCase("sign")
					|| name.equalsIgnoreCase("sign_type")) {
				continue;
			}

			properties.setProperty(name, value.toString());

		}

		String content = getSignatureContent(properties);
		return sign(content, privateKey);
	}

	public static String getSignatureContent(Properties properties) {
		StringBuffer content = new StringBuffer();
		List keys = new ArrayList(properties.keySet());
		Collections.sort(keys);

		for (int i = 0; i < keys.size(); i++) {
			String key = (String) keys.get(i);
			String value = properties.getProperty(key);

			content.append((i == 0 ? "" : "&") + key + "=" + value);
		}

		return content.toString();
	}

	public static String sign(String content, String privateKey) {
		if (privateKey == null) {
			return null;
		}
		String signBefore = content + privateKey;
		//System.out.print("signBefore=" + signBefore);
		//*****************************************************************
		//When alipay received information, the information will be accepted to write the log-way
		//The existence of the file and application servers in the same directory startup file, the file name is added alipay log server
		try {
			FileWriter writer  = new FileWriter("D:/alipay_log/"+"alipay_log"+"alipay_log"+System.currentTimeMillis()+".txt");
			writer.write(signBefore);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
//*********************************************************************
		return Md5Encrypt.md5(signBefore);

	}

}
