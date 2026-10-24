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
        <span class="value" id="visionStateCard"><#if ((st.visionConfigured)!'N') == 'Y'><span class="ok">已配置</span><#else><span class="warn">未配置</span></#if></span></span>
    <span class="card"><span class="label">识别密钥</span>
        <span class="value" id="visionKeyCard"><#if ((st.visionKeySet)!'N') == 'Y'>已设置<#else><span class="hint">未设置</span></#if></span></span>
    <span class="card"><span class="label">配置来自</span>
        <span class="value" id="visionSourceCard"><#if ((st.visionSource)!'') == 'DATABASE'>本页保存<#elseif ((st.visionSource)!'') == 'JVM'>启动参数 -D</#if></span></span>
</p>
<#if ((st.visionMessage)!'') != ''>
    <p class="warn">${st.visionMessage}</p>
</#if>

<#if canManage>
    <#-- only the pair that has really been tried is offered: a list of other providers the
         shop has never tested is how people end up with an empty answer and no idea why -->
    <#assign visionEndpointValue = (st.visionEndpoint)!''>
    <#if visionEndpointValue == ''><#assign visionEndpointValue = 'https://api.deepseek.com/v1/chat/completions'></#if>
    <#assign visionModelValue = (st.visionModel)!''>
    <#if visionModelValue == ''><#assign visionModelValue = 'deepseek-flash'></#if>
    <fieldset>
        <legend>识图接口</legend>
        <form method="post" action="/mfg/control/settingsSave" id="visionForm">
            <p>
                接口地址 <input type="text" name="visionEndpoint" size="52" value="${visionEndpointValue}"/>
                <span class="hint">已按 DeepSeek 填好，一般不用改</span>
            </p>
            <p>
                模型名 <input type="text" name="visionModel" size="30" value="${visionModelValue}"/>
                <span class="hint">本系统实测过的就是它（<code>deepseek-flash</code>）</span>
            </p>
            <p>
                密钥 <input type="password" name="visionKey" size="40" value=""
                    placeholder="<#if ((st.visionKeySet)!'N') == 'Y'>已设置，留空不修改<#else>把 API Key 粘到这里</#if>"/>
                <span class="hint">在 DeepSeek 控制台生成；只写库、不回显，留空表示不改</span>
            </p>
            <p>
                <input type="submit" value="保存"/>
                <button type="button" id="testVision">测试连接</button>
                <span id="testHint" class="hint">不用先保存：拿页面上的地址 / 模型 / 密钥直接测，成功会顺手保存</span>
            </p>
        </form>
        <form method="post" action="/mfg/control/settingsSave" class="inline">
            <input type="hidden" name="clear" value="Y"/>
            <input type="submit" value="清除识图配置" class="secondary"/>
        </form>
    </fieldset>
    <p class="hint">没配置也能用：「从淘宝订单建单并入库」里把明细粘进去就行，识别只是省一次打字。</p>
    <p class="hint" id="visionHelp">
        识图只有一套实测过的组合：DeepSeek + <code>deepseek-flash</code>（上面已填好）。
        要做的就一件事——把 DeepSeek 控制台里的 API Key 粘进「密钥」，点「测试连接」；
        能回一句话就说明三项都对，设置同时保存。
    </p>
    <script>
    /* a wrong address, key or model should be found here, not in the middle of an order; the form
       is what gets tested, so nobody has to save first and wonder why the test says "not set" */
    (function () {
        var button = document.getElementById('testVision');
        var hint = document.getElementById('testHint');
        var form = document.getElementById('visionForm');
        if (!button || !hint || !form || !window.fetch) {
            return;
        }
        button.addEventListener('click', function () {
            hint.className = 'hint';
            hint.textContent = '正在测试…';
            button.className = 'busy';
            fetch('/mfg/control/visionTest', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body: new URLSearchParams(new FormData(form))
            })
                .then(function (answer) { return answer.json(); })
                .then(function (data) {
                    hint.className = data.ok ? 'ok' : 'error';
                    hint.textContent = data.message || (data.ok ? '连接成功' : '连接失败');
                    if (data.ok) {
                        markConfigured();
                    }
                    button.className = '';
                })
                .catch(function () {
                    hint.className = 'error';
                    hint.textContent = '测试请求失败，看看服务是否还在运行';
                    button.className = '';
                });
        });
        /* a successful test saves the settings, so the three cards must not still say 未配置 */
        function markConfigured() {
            var state = document.getElementById('visionStateCard');
            var key = document.getElementById('visionKeyCard');
            var source = document.getElementById('visionSourceCard');
            if (state) { state.innerHTML = '<span class="ok">已配置</span>'; }
            if (key) { key.textContent = '已设置'; }
            if (source) { source.textContent = '本页保存'; }
            var field = form.querySelector('input[name="visionKey"]');
            if (field) { field.value = ''; }
        }
    }());
    </script>
<#else>
    <p class="warn">只有管理员可以修改这些设置。</p>
</#if>
