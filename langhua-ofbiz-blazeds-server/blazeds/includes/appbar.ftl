<#--
This library is part of  OFBIZ-JFreeChart1.0.10 component component  of Langhua
 
Copyright (C) 2008  Beijing Langhua Ltd. (http://www.langhua.cn)
 
This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.
 
This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Lesser General Public License for more details.

For the latest version about this module, please see the
project website: http://www.langhua.cn/langhua/ofbiz-components/OFBIZ-JFreeChart1.0.10
 
For more information on Apache OFBiz, please see the
project website: http://ofbiz.apache.org/

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
-->

<#assign selected = headerItem?default("void")>

<div id="app-navigation">
  <h2>
    <#if sessionAttributes.userLogin?has_content && sessionAttributes.userLogin.userLoginId != ""><#-- permission -->

   </h2>
  <br class="clear"/>
</div>
