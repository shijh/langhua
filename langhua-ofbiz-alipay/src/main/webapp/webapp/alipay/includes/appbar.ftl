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

<#assign selected = headerItem?default("void")>

<div id="app-navigation">
<#--
  <h2>
  <ul>
    <li<#if selected = "main"> class="selected"</#if>><a href="<@ofbizUrl>main</@ofbizUrl>">首页</a></li>
    <li<#if selected = "alipay"> class="selected"</#if>><a href="<@ofbizUrl>alipay</@ofbizUrl>">支付宝</a></li>
    <#if userLogin?has_content>
     <li class="opposed"><a href="<@ofbizUrl>logout</@ofbizUrl>">注销</a></li>
    <#else>
      <li class="opposed"><a href="<@ofbizUrl>${checkLoginUrl?if_exists}</@ofbizUrl>">登录</a></li>
    </#if>
  </ul></h2>
  <br class="clear"/>-->
</div>