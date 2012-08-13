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

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.collections.map.LinkedMap;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilFormatOut;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;

/**
 * @author Wang Suozhu
 *
 */
public class AlipayEvents {

	
    public static final String module = AlipayEvents.class.getName();

    
    /** Click the category dealing with OFBiz.
     *@param request The HTTPRequest object for the current request
     *@param response The HTTPResponse object for the current request
     *@return String specifying the exit status of this event
     */
    public static String callAlipay(HttpServletRequest request, HttpServletResponse response) {
        Locale locale = UtilHttp.getLocale(request);
        GenericDelegator delegator = (GenericDelegator) request.getAttribute("delegator");
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin"); 
        // get the orderId
        String orderId = (String) request.getAttribute("orderId");
        
        // get the order header
        GenericValue orderHeader = null;
        try {
            orderHeader = delegator.findByPrimaryKey("OrderHeader", UtilMisc.toMap("orderId", orderId));
        } catch (GenericEntityException e) {
            Debug.logError(e, "Cannot get the order header for order: " + orderId, module);
            request.setAttribute("_ERROR_MESSAGE_", "Problems getting order header.");
            return "error";
        }
        
        // get the payment properties file   
        String configString = "alipay.properties";
        
        // get the redirect url
        String redirectUrl = UtilProperties.getPropertyValue(configString, "payment.alipay.redirecturl");
        //String redirectUrl = "https://www.alipay.com/cooperate/gateway.do?_input_charset=utf-8";
        String paygateway = UtilProperties.getPropertyValue(configString, "alipay.paygateway");   
        String service = UtilProperties.getPropertyValue(configString, "alipay.service"); 
        String partner = UtilProperties.getPropertyValue(configString, "alipay.partner");
    	String key = UtilProperties.getPropertyValue(configString, "alipay.partner.key");
    	String seller_email = UtilProperties.getPropertyValue(configString, "alipay.partner.seller_email");
    	String input_charset = "utf-8";
    	String sign_type = "MD5";
    	String out_trade_no = orderId;
    	String payment_type = "1";//Payment type
    	String discount = "0"; //Do not discount
    	
    	String logistics_payment = "BUYER_PAY";  
    	
    	String path = request.getScheme() + "://" + request.getServerName()	+ ":" + request.getServerPort();
    	String return_url = path + UtilProperties.getPropertyValue(configString, "alipay.partner.return_url");
    	String notify_url = path + UtilProperties.getPropertyValue(configString, "alipay.partner.notify_url");
    	String body = "goods"; 
    	String subject = "goods"; 
    	double fee = 0;
    	try {
    	
    	List orderAdjustments = delegator.findByAnd("OrderAdjustment", UtilMisc.toMap("orderId",orderId,"orderAdjustmentTypeId","SHIPPING_CHARGES")); //
    	
    	Iterator ite = orderAdjustments.iterator();
    	
        while (ite.hasNext()) {
            GenericValue orderAdjustment = (GenericValue) ite.next();
            fee = orderAdjustment.getDouble("amount").doubleValue();
            
          
        }
    	} catch (Exception e) {
			
		}
    	//Shipping method
    	String  shipmentMethodTypeId = "";
    	try {
        	
    	GenericValue orderItemShipGroup = delegator.findByPrimaryKey("OrderItemShipGroup", UtilMisc.toMap("orderId",orderId,"shipGroupSeqId","00001")); //
    	
    	 shipmentMethodTypeId = orderItemShipGroup.getString("shipmentMethodTypeId");
        } catch (Exception e) {
			
		}
        
        String logistics_fee = java.lang.String.valueOf(fee); 
        double total = orderHeader.getDouble("grandTotal").doubleValue()-fee;
        String orderTotal = UtilFormatOut.formatPrice(Double.valueOf(total));
        
    	String logistics_type = "EXPRESS";  
    	
    	if("POST".equals(shipmentMethodTypeId))
    		logistics_type = "POST";
    	else if("EMS".equals(shipmentMethodTypeId))
    		logistics_type = "EMS";
    	else 
    		logistics_type = "EXPRESS"; 
    		
    	String price = orderTotal; 
    	
    	String show_url = path + UtilProperties.getPropertyValue(configString, "alipay.show_url");
    	
    	String quantity = "1";
    	double counter = 0;//Order the quantity of goods in
    	String productName = "";
    	int i = 0;
    	try {
        	
        	List orderItems = delegator.findByAnd("OrderItem", UtilMisc.toMap("orderId",orderId)); //
        	
        	Iterator ite = orderItems.iterator();
        	
            while (ite.hasNext()) {
                GenericValue orderItem = (GenericValue) ite.next();
                String productId = orderItem.getString("productId");
                if (productId != null) {
                    try {
                                          
                        GenericValue product = delegator.findByPrimaryKey("Product", UtilMisc.toMap("productId", productId));
                        if(i >= 1) productName = productName + "|";
                        double qua =orderItem.getDouble("quantity").doubleValue();
                        productName = productName + product.getString("productName").trim()+"("+UtilFormatOut.formatQuantity(qua)+")";
                        counter = counter + qua;
                       
                    } catch (Exception e) {
                        
                    }
                } 
                i++;
            }
        	} catch (Exception e) {
    			
    		}
    	
        if(i > 1){
        	 body = productName.trim();
        	 subject = out_trade_no;
        	 }
        else if(i == 1){
        	  subject = productName.trim() + "-"+out_trade_no;
        	  body = productName.trim();
        	}
        
        if(i > 1)       
        	quantity = "1";
        else if(i == 1)
        	{ quantity = UtilFormatOut.formatQuantity(counter);
        	
        	  price = UtilFormatOut.formatPrice(Double.valueOf(total/counter));//The average unit price of goods
        	  
        	}
        
        if (UtilValidate.isEmpty(redirectUrl) 
                || UtilValidate.isEmpty(service) 
                || UtilValidate.isEmpty(partner) 
                || UtilValidate.isEmpty(key) 
                || UtilValidate.isEmpty(seller_email) ) {
                Debug.logError("Payment properties is not configured properly, some notify URL from Alipay is not correctly defined!", module);
                return "error";
            }
        //By passing parameters to return a signed MD5 encrypted string
        String ItemUrl = AlipayPayment.CreateUrl(paygateway, service, sign_type,out_trade_no, input_charset, partner, key, seller_email,body, subject, price, quantity, show_url, payment_type,discount, logistics_type, logistics_fee, logistics_payment,return_url,notify_url);
        // create the redirect string
        Map parameters = new LinkedMap();
        parameters.put("service", service);
        parameters.put("partner", partner);
        parameters.put("sign_type", sign_type);
        parameters.put("out_trade_no", out_trade_no);
        //parameters.put("input_charset", input_charset);
        parameters.put("sign", ItemUrl);
        parameters.put("seller_email", seller_email);
        parameters.put("body", body);
        parameters.put("subject", subject);
        parameters.put("price", price);
        parameters.put("quantity", quantity);        // no notes allowed in alipay (not passed back)
        parameters.put("show_url", show_url);    // no shipping address required (local shipping used)
        parameters.put("payment_type", payment_type);
        parameters.put("discount", discount);
        parameters.put("logistics_type", logistics_type);
        parameters.put("logistics_fee", logistics_fee);
        parameters.put("logistics_payment", logistics_payment);
        parameters.put("return_url", return_url);
        parameters.put("notify_url", notify_url);
                
        String encodedParameters = UtilHttp.urlEncodeArgs(parameters, false);
        String redirectString = redirectUrl + "&" + encodedParameters;   
        
        // set the order in the session for cancelled orders
        request.getSession().setAttribute("ALIPAY_ORDER", orderId); 
        
        // redirect to alipay
        try {
            response.sendRedirect(redirectString);
        } catch (IOException e) {
            Debug.logError(e, "Problems redirecting to AliPay", module);
            request.setAttribute("_ERROR_MESSAGE_", "Problems connecting with Alipay, please contact customer service.");
            return "error";
        }
        
        return "success";   
    }
    
}

