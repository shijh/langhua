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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alipay.util.*;

/**
 * @author Wang Suozhu
 *
 */
public class AlipayPayment extends Payment{
	/** By passing parameters to return a signed MD5 encrypted string
     *@param paygateway  Payment interface
     *@param service Service parameters
     *@param sign_type Signature type
     *@param out_trade_no  Business Web site orders
     *@param input_charset Page code
     *@param key Home Safety Check (account extract)
     *@param seller_email Email the seller account
     *@param body Description of goods
     *@param price Order Price
     *@param quantity Order Amount
     *@param show_url Display the address number of commodities
     *@param payment_type  Payment type
     *@param discount Discount
     *@param logistics_type Logistics distribution approach
     *@param logistics_fee Logistics and distribution costs
     *@param logistics_payment Logistics and distribution costs of payment
     *@param return_url Jump back to pay after the completion of the Web site URL
     *@param notify_url End pay shall notify the page after the server
     *@return Return a string MD5 encrypted signature
     */
   public static String CreateUrl(String paygateway,String service,String sign_type,String out_trade_no,
		      String input_charset,String partner,String key,String seller_email,
            String body,String subject,String price,String quantity,String show_url,String payment_type,
            String discount,String logistics_type,String logistics_fee,String logistics_payment,
            String return_url,String notify_url) {
  Map params = new HashMap();
  params.put("service", service);
  params.put("out_trade_no", out_trade_no);
  params.put("show_url", show_url);
  params.put("quantity", quantity);
  params.put("partner", partner);
  params.put("payment_type", payment_type);
  params.put("discount", discount);
  params.put("body", body);
  params.put("notify_url", notify_url);
  params.put("price", price);
  params.put("return_url", return_url);
  params.put("seller_email", seller_email);
  params.put("logistics_type", logistics_type);
  params.put("logistics_fee", logistics_fee);
  params.put("logistics_payment", logistics_payment);
  params.put("subject", subject);
  params.put("_input_charset", input_charset);
  String prestr = "";

  prestr = prestr + key;
  //System.out.println("prestr=" + prestr);

  String sign = Md5Encrypt.md5(getContent(params, key));

  String parameter = "";
  parameter = parameter + paygateway;
  //System.out.println("prestr="  + parameter);
  List keys = new ArrayList(params.keySet());
  for (int i = 0; i < keys.size(); i++) {
    	String value =(String) params.get(keys.get(i));
      if(value == null || value.trim().length() ==0){
      	continue;
      }
      try {
          parameter = parameter + keys.get(i) + "="
              + URLEncoder.encode(value, input_charset) + "&";
      } catch (UnsupportedEncodingException e) {

          e.printStackTrace();
      }
  }

  parameter = parameter + "sign=" + sign + "&sign_type=" + sign_type;

  return sign;

}


private static String getContent(Map params, String privateKey) {
  List keys = new ArrayList(params.keySet());
  Collections.sort(keys);

  String prestr = "";

	boolean first = true;
	for (int i = 0; i < keys.size(); i++) {
		String key = (String) keys.get(i);
		String value = (String) params.get(key);
		if (value == null || value.trim().length() == 0) {
			continue;
		}
		if (first) {
			prestr = prestr + key + "=" + value;
			first = false;
		} else {
			prestr = prestr + "&" + key + "=" + value;
		}
	}
  return prestr + privateKey;
}
}
