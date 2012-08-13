/*
 * This library is part of OFBiz-AliPay Component of Langhua
 *
 * Copyright (C) 2009  Langhua Opensource Foundation (http://langhua.org)
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
 * For the latest version about this component, please see the
 * project website: http://langhua.org/opensource/ofbiz/ofbiz-alipay/
 * 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 * 
 * For more information on AliPay, please see the its website:
 * https://www.alipay.com/
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.ofbiz.alipay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.alipay.util.Md5Encrypt;
import com.alipay.util.SignatureHelper;

/**
 * @author Wang Suozhu
 *
 */
public class AlipaySignatureHelper extends SignatureHelper{

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
	//System.out.print("nsignBefore="+signBefore);
	//*****************************************************************
	//When alipay received information, the information will be accepted to write the log-way
	//The existence of the file and application servers in the same directory startup file, the file name is added alipay log server
    //Be canceled out
	/*try {
		FileWriter writer  = new FileWriter("D:/alipay_log/"+"alipay_log"+"alipay_log"+System.currentTimeMillis()+".txt");
		writer.write(signBefore);
		writer.close();
	} catch (Exception e) {
		e.printStackTrace();
	}*/
//*********************************************************************
	return Md5Encrypt.md5(signBefore);

}
}
