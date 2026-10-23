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
                接口地址 <input type="text" name="visionEndpoint" size="52" value="${(st.visionEndpoint)!''}" placeholder="https://api.deepseek.com/v1/chat/completions"/>
                <span class="hint">要填到 <code>/chat/completions</code>；只填域名时会自动补成 <code>/v1/chat/completions</code></span>
            </p>
            <p>
                模型名 <input type="text" name="visionModel" size="30" value="${(st.visionModel)!''}" placeholder="能看图的模型名"/>
                <span class="hint">必须是<strong>能看图</strong>的模型（例如 qwen-vl-max、glm-4v-plus、gpt-4o-mini）；纯文本模型会把图片当没看见</span>
            </p>
            <p>
                密钥 <input type="password" name="visionKey" size="40" value=""
                    placeholder="<#if ((st.visionKeySet)!'N') == 'Y'>已设置，留空不修改<#else>填 Bearer Token</#if>"/>
                <span class="hint">只写库、不回显；留空表示保持原来的密钥</span>
            </p>
            <p>
                <input type="submit" value="保存"/>
                <button type="button" id="testVision">测试连接</button>
                <span id="testHint" class="hint">保存后点一下，确认地址 / 模型 / 密钥都对</span>
            </p>
        </form>
        <form method="post" action="/mfg/control/settingsSave" class="inline">
            <input type="hidden" name="clear" value="Y"/>
            <input type="submit" value="清除识图配置" class="secondary"/>
        </form>
    </fieldset>
    <p class="hint">没配置也能用：「从淘宝订单建单并入库」里把明细粘进去就行，识别只是省一次打字。</p>
    <p class="hint">地址与模型要配对，几个现成可用的：<br/>
        阿里云百炼 <code>https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions</code> + <code>qwen-vl-max</code>；<br/>
        智谱 <code>https://open.bigmodel.cn/api/paas/v4/chat/completions</code> + <code>glm-4v-plus</code>；<br/>
        DeepSeek <code>https://api.deepseek.com/v1/chat/completions</code> + <code>deepseek-flash</code>（能看图；<code>deepseek-v4-pro</code> 不看图）；<br/>
        OpenAI <code>https://api.openai.com/v1/chat/completions</code> + <code>gpt-4o-mini</code>；<br/>
        本地 ollama <code>http://127.0.0.1:11434/v1/chat/completions</code> + <code>qwen2-vl</code>（密钥随便填）。<br/>
        <strong>模型名要先确认它能看图</strong>，纯文本的模型会返回空明细。</p>
    <script>
    /* a wrong address, key or model should be found here, not in the middle of an order */
    (function () {
        var button = document.getElementById('testVision');
        var hint = document.getElementById('testHint');
        if (!button || !hint || !window.fetch) {
            return;
        }
        button.addEventListener('click', function () {
            hint.className = 'hint';
            hint.textContent = '正在测试…';
            fetch('/mfg/control/visionTest', { method: 'POST', credentials: 'same-origin' })
                .then(function (answer) { return answer.json(); })
                .then(function (data) {
                    hint.className = data.ok ? 'ok' : 'error';
                    hint.textContent = data.message || (data.ok ? '连接成功' : '连接失败');
                })
                .catch(function () {
                    hint.className = 'error';
                    hint.textContent = '测试请求失败，看看服务是否还在运行';
                });
        });
    }());
    </script>
<#else>
    <p class="warn">只有管理员可以修改这些设置。</p>
</#if>
