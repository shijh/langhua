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
<#assign workflowList = Static["org.langhua.ofbiz.jbpm.OFBizJBPMList"].getInstance().getListItems(delegator, userLogin)>
  <table class="basic-table" cellspacing="0">
  <tr>   
          <td class="header-row">${uiLabelMap.GUI_JBPM_PROCESS_IMAGE_0}</td>
          <td class="header-row">${uiLabelMap.GUI_JBPM_PROCESS_STOP_0}</td>
          <td class="header-row">${uiLabelMap.GUI_JBPM_PROCESS_ID_0}</td>   
          <td class="header-row">${uiLabelMap.GUI_JBPM_PROCESS_NAME_0}</td> 
          <td class="header-row">${uiLabelMap.GUI_JBPM_PROCESS_TASKNAME_0}</td>
          <td class="header-row">${uiLabelMap.GUI_JBPM_PROCESS_STATUS_0}</td>
  </tr>
  <#if workflowList?has_content>
    <#list workflowList as providerObj>
      <tr>   
          <td class="button-col"><a href="<@ofbizUrl>popupprocessimage?taskInstanceId=${providerObj.taskInstanceId}&processDefinitionId=${providerObj.processDefinitionId}&tokenInstanceId=${providerObj.tokenId}</@ofbizUrl>"  target="_blank">${uiLabelMap.GUI_JBPM_VIEW_0}</a></td> 
          <td class="button-col"><a href="<@ofbizUrl>endProcess?processInstanceId=${providerObj.processInstanceId}</@ofbizUrl>">${uiLabelMap.GUI_JBPM_OK_0}</a></td>   
          <td class="button-col">${providerObj.taskInstanceId}</td>   
          <td class="button-col">${providerObj.processDefinitionName}</td> 
          <td class="button-col"><a href="<@ofbizUrl>taskInstance?taskInstanceName=${providerObj.taskInstanceName?if_exists}&taskInstanceId=${providerObj.taskInstanceId}&processDefinitionId=${providerObj.processDefinitionId}&tokenInstanceId=${providerObj.tokenId}&processInstanceId=${providerObj.processInstanceId}</@ofbizUrl>">${providerObj.taskInstanceName}</a></td>
          <td class="button-col"><a href="<@ofbizUrl>processDetail?processInstanceId=${providerObj.processInstanceId}</@ofbizUrl>">${uiLabelMap.GUI_JBPM_VIEW_0}</a></td>   
      </tr>
    </#list>
  </#if>   
  </table>