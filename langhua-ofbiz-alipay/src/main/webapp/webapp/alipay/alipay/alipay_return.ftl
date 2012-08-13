<#--
This library is part of OFBiz-AliPay Component of Langhua
 
Copyright (C) 2009  Langhua Opensource Foundation (http://langhua.org)
 
This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3.0 of the License, or (at your option) any later version.
 
This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Lesser General Public License for more details.

For the latest version about this module, please see the
project website: http://langhua.org/opensource/ofbiz/ofbiz-alipay/
 
For more information on Apache OFBiz, please see the
project website: http://ofbiz.apache.org/

For more information on AliPay, please see its website:
https://www.alipay.com/

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
-->
                <#assign flag = request.getAttribute("flag")?if_exists/>
                <#if flag == "true"> 
                <#assign get_order = request.getAttribute("get_order")?if_exists/>
                <#assign get_total_fee = request.getAttribute("get_total_fee")?if_exists/>
                <#assign get_subject = request.getAttribute("get_subject")?if_exists/>
                <#assign get_body = request.getAttribute("get_body")?if_exists/>
                <#assign result = request.getAttribute("result")?if_exists/>
                <#assign trade_status = request.getAttribute("trade_status")?if_exists/>
                <#assign return_url = request.getAttribute("return_url")?if_exists/>
                显示订单信息如下<br>
				订单号:${get_order?if_exists}  <br>
				订单总价:${get_total_fee?if_exists} <br>
				商品名称:${get_subject?if_exists} <br>
				商品描述:${get_body?if_exists}  <br>
				交易状态:${result?if_exists}[${trade_status?if_exists}]<br>
				<#--${responseTxt?if_exists}-->
				<br><a href="${return_url?if_exists}">返回继续购物</a>
				<#elseif flag=="fail">
				<#assign mysign = request.getAttribute("mysign")?if_exists/>
				<#assign sign = request.getAttribute("sign")?if_exists/>
				${mysign?if_exists} ---------------- ${sign?if_exists} <br>
				支付失败
				</#if>
				