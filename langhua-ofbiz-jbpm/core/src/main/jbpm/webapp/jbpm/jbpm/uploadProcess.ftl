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

<#if permission=="true">
<table>
<table>
     <tr>
          <td ><h1>${uiLabelMap.GUI_JBPM_UPLOAD_DEFINITION_0}</h1></td>
    </tr>
</table>  

<form method="post" enctype="multipart/form-data" action="<@ofbizUrl>upload</@ofbizUrl>" name="uploadForm">
    <input type="file" accept="application/zip" size="50" name="fname" />
    <br/>
    <input type="submit" class="smallSubmit" value="${uiLabelMap.GUI_JBPM_UPLOAD_0}"/>
</form>
<hr class="sepbar"/>
</table>
<#else>
<div>${uiLabelMap.GUI_JBPM_NOTADMIN_0}</div>
</#if>