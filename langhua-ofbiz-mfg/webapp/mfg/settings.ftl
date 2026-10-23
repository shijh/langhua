<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  系统设置：把原先要写进启动脚本的 -D 参数搬到页面上（存 SystemProperty，资源 mfg）。
  数据：settings.visionEndpoint / visionModel / visionKeySet / visionConfigured / visionSource /
        canManage / visionMessage
-->
<#assign st = (settings)!{}>
<#assign errList = (errorMessageList)![]>
<#assign canManage = ((st.canManage)!'N') == 'Y'>

<h1>系统设置</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<h2 id="visionHeading">采购订单截图识别</h2>
<p class="cards" id="visionCards" data-configured="${(st.visionConfigured)!'N'}" data-source="${(st.visionSource)!'NONE'}">
    <span class="card"><span class="label">状态</span>
        <span class="value"><#if ((st.visionConfigured)!'N') == 'Y'><span class="ok">已配置</span><#else><span class="warn">未配置</span></#if></span></span>
    <span class="card"><span class="label">识别密钥</span>
        <span class="value"><#if ((st.visionKeySet)!'N') == 'Y'>已设置<#else><span class="hint">未设置</span></#if></span></span>
    <span class="card"><span class="label">配置来自</span>
        <span class="value"><#if ((st.visionSource)!'') == 'DATABASE'>本页保存<#elseif ((st.visionSource)!'') == 'JVM'>启动参数 -D</#if></span></span>
</p>
<#if ((st.visionMessage)!'') != ''>
    <p class="warn">${st.visionMessage}</p>
</#if>

<#if canManage>
    <fieldset>
        <legend>识图接口（兼容 OpenAI 的 chat/completions）</legend>
        <form method="post" action="/mfg/control/settingsSave">
            <p>
                接口地址 <input type="text" name="visionEndpoint" size="52" value="${(st.visionEndpoint)!''}" placeholder="https://&lt;host&gt;/v1/chat/completions"/>
            </p>
            <p>
                模型名 <input type="text" name="visionModel" size="30" value="${(st.visionModel)!''}" placeholder="能看图的模型名"/>
            </p>
            <p>
                密钥 <input type="password" name="visionKey" size="40" value=""
                    placeholder="<#if ((st.visionKeySet)!'N') == 'Y'>已设置，留空不修改<#else>填 Bearer Token</#if>"/>
                <span class="hint">只写库、不回显；留空表示保持原来的密钥</span>
            </p>
            <p>
                <input type="submit" value="保存"/>
                <span class="hint">保存后，到「采购到货」页的「从淘宝订单建单并入库」里就能用「识别并填入」。</span>
            </p>
        </form>
        <form method="post" action="/mfg/control/settingsSave" class="inline">
            <input type="hidden" name="clear" value="Y"/>
            <input type="submit" value="清除识图配置" class="secondary"/>
        </form>
    </fieldset>
    <p class="hint">没配置也能用：「从淘宝订单建单并入库」里把明细粘进去就行，识别只是省一次打字。</p>
<#else>
    <p class="warn">只有管理员可以修改这些设置。</p>
</#if>
