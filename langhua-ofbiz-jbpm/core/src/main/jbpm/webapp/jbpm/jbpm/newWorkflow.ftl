<#--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
<#assign processList = Static["org.langhua.ofbiz.jbpm.OFBizNewProcessInstance"].getWorkflowOptions(delegator,userLogin)>
<div>
<form action="<@ofbizUrl>buildWorkflow</@ofbizUrl>" method="post" name="buildWorkflow">
<table border="1" cellpadding="2" cellspacing="0" >   

    <tr>
        <td colspan='2'> <h1>${uiLabelMap.GUI_JBPM_START_NEW_PROCESS_0}</h1>   </td> 
    </tr>
    <tr>
        <td class="label">${uiLabelMap.GUI_JBPM_CHOOSE_PROCESS_0}</td>
        <td>
             <select name="processname">  
                  <#list processList as process> 
                       <option value="${process?if_exists}">${process?if_exists}</option>
                  </#list>
             </select>
        </td>     
     </tr>
     <tr>
         <td class="label"> </td>
         <td  align="left" >
              <input type="submit" value="${uiLabelMap.GUI_JBPM_SUBMIT_0}" />
         </td>    
     </tr> 
</table>  
</form>  
</div>
