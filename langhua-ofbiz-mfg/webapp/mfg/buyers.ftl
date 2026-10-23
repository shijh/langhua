<#ftl encoding="UTF-8">
<#import "pager.ftl" as pg>
<#setting url_escaping_charset="UTF-8">
<#--
  Internal buyer risk list. A hit only shows a warning before shipping: the law and the marketplace
  still require us to serve every legal after sales request, so nothing is blocked here.
  Data: flagList.flags, flagList.flagCount, flagList.blockCount, riskList.buyers, checkResult.*
-->
<#assign fl = (flagList)!{}>
<#assign flags = (fl.flags)![]>
<#assign rl = (riskList)!{}>
<#assign buyers = (rl.buyers)![]>
<#assign chk = (checkResult)!{}>
<#assign errList = (errorMessageList)![]>
<#macro levelLabel level>
    <#if (level!'') == 'BLOCK'><span class="error">人工复核</span>
    <#elseif (level!'') == 'WATCH'><span class="warn">观察</span>
    <#else><span class="hint">-</span></#if>
</#macro>
<#macro reasonLabel code>
    <#if (code!'') == 'FREQ_RETURN'>高频无理由退货
    <#elseif (code!'') == 'EMPTY_BOX'>空包/少件
    <#elseif (code!'') == 'SWAP'>调包
    <#elseif (code!'') == 'ABUSE_7DAY'>滥用 7 天无理由
    <#elseif (code!'') == 'FAKE_CLAIM'>虚假质量问题
    <#elseif (code!'') == 'THREAT'>差评勒索
    <#else>-</#if>
</#macro>

<h1>买家风险名单（内部）</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<p class="banner">
    <b>使用边界：</b>名单只在<b>发货前</b>提示（加强留证、复核后再发、不给赠品），
    <b>不拒绝任何法定售后</b>——质量问题三包与 7 天无理由必须照做。
    命中 BLOCK 时系统只提示，不会拦截售后单登记或出货。
</p>

<p>
    名单共 <span class="big">${(fl.flagCount)!0}</span> 条，其中需人工复核 <span class="big error">${(fl.blockCount)!0}</span> 条。
</p>

<fieldset>
    <legend>登记 / 修改名单</legend>
    <form method="post" action="/mfg/control/buyerFlagSave">
        <p>
            收货手机号 <input type="text" name="receiverMobile" size="16" placeholder="11 位真号；脱敏号不可用"/>
            买家昵称 <input type="text" name="buyerNick" size="16" placeholder="主匹配键"/>
            收件人 <input type="text" name="receiverName" size="10"/>
        </p>
        <p>
            风险等级
            <select name="riskLevel">
                <option value="WATCH">观察（只提示）</option>
                <option value="BLOCK">人工复核（发货前必看）</option>
            </select>
            原因
            <select name="reasonCode">
                <option value="">（未选）</option>
                <option value="FREQ_RETURN">高频无理由退货</option>
                <option value="EMPTY_BOX">空包/少件</option>
                <option value="SWAP">调包</option>
                <option value="ABUSE_7DAY">滥用 7 天无理由</option>
                <option value="FAKE_CLAIM">虚假质量问题</option>
                <option value="THREAT">差评勒索</option>
            </select>
            收货地址 <input type="text" name="addressText" size="30" placeholder="可选"/>
        </p>
        <p>
            证据说明 <input type="text" name="evidenceNote" size="62" placeholder="聊天截图/开箱视频文件名/平台申诉单号，用于日后申诉"/>
        </p>
        <p>
            备注 <input type="text" name="note" size="26"/>
            到期日 <input type="text" name="expiresDate" size="10" placeholder="留空 = 12 个月后自动失效"/>
            <input type="submit" value="保存"/>
        </p>
    </form>
    <p class="hint">匹配键以<b>买家昵称</b>为主（订单上一定有），手机号是次要键且<b>只收 11 位真号</b>：
        脱敏号（如 <code>138****2222</code>）会被拒绝，否则不同买家会撞到同一个键。
        名单默认 12 个月后自动失效，避免“永久拉黑”的合规风险。</p>
</fieldset>

<fieldset>
    <legend>发货前查一下</legend>
    <form method="get" action="/mfg/control/buyers">
        手机号 <input type="text" name="checkMobile" size="16" value="${(chk.receiverMobile)!}"/>
        昵称 <input type="text" name="checkNick" size="16" value="${(chk.buyerNick)!}"/>
        <input type="submit" value="查询"/>
    </form>
    <#if (chk.hit)??>
        <#if (chk.hit!'N') == 'Y'>
            <p class="error">命中名单，风险等级：<@levelLabel (chk.riskLevel)!''/></p>
            <table>
                <tr><th>匹配键</th><th>手机号</th><th>昵称</th><th>等级</th><th>原因</th><th>证据</th></tr>
                <#list (chk.flags)![] as f>
                    <tr>
                        <td>${f.buyerKey!}</td>
                        <td>${f.maskedMobile!}</td>
                        <td>${f.buyerNick!}</td>
                        <td><@levelLabel (f.riskLevel)!''/></td>
                        <td><@reasonLabel (f.reasonCode)!''/></td>
                        <td>${f.evidenceNote!}</td>
                    </tr>
                </#list>
            </table>
        <#else>
            <p class="ok">未命中名单。</p>
        </#if>
    </#if>
</fieldset>

<h2>名单列表</h2>
<form method="get" action="/mfg/control/buyers">
    关键字 <input type="text" name="keyword" size="18" value="${(fl.keyword)!}"/>
    等级
    <select name="riskLevel">
        <option value="">（全部）</option>
        <option value="WATCH"<#if (parameters.riskLevel!'') == 'WATCH'> selected="selected"</#if>>观察</option>
        <option value="BLOCK"<#if (parameters.riskLevel!'') == 'BLOCK'> selected="selected"</#if>>人工复核</option>
    </select>
    状态
    <select name="statusId">
        <option value="">（全部）</option>
        <option value="MFG_FLAG_ACTIVE"<#if (parameters.statusId!'') == 'MFG_FLAG_ACTIVE'> selected="selected"</#if>>生效中</option>
        <option value="MFG_FLAG_RELEASED"<#if (parameters.statusId!'') == 'MFG_FLAG_RELEASED'> selected="selected"</#if>>已解除</option>
    </select>
    <input type="submit" value="筛选"/>
    <span class="hint">共 ${(fl.totalCount)!0} 条</span>
</form>

<#if flags?has_content>
    <table>
        <tr><th>匹配键</th><th>手机号</th><th>昵称</th><th>收件人</th><th>等级</th><th>原因</th>
            <th>证据</th><th>状态</th><th>到期日</th><th>登记时间</th><th>操作</th></tr>
        <#list flags as f>
            <tr>
                <td>${f.buyerKey!}</td>
                <td>${f.maskedMobile!}</td>
                <td>${f.buyerNick!}</td>
                <td>${f.receiverName!}</td>
                <td><@levelLabel (f.riskLevel)!''/></td>
                <td><@reasonLabel (f.reasonCode)!''/></td>
                <td>${f.evidenceNote!}</td>
                <td><#if (f.statusId!'') == 'MFG_FLAG_ACTIVE'><span class="warn">生效中</span>
                    <#else><span class="hint">已解除<#if f.releasedDate??>（${f.releasedDate?string("yyyy-MM-dd")}）</#if></span></#if></td>
                <td><#if f.expiresDate??>${f.expiresDate?string("yyyy-MM-dd")}<#else>-</#if>
                    <#if (f.live!'N') == 'N'><span class="hint">（已失效）</span></#if></td>
                <td><#if f.createdStamp??>${f.createdStamp?string("yyyy-MM-dd HH:mm")}</#if></td>
                <td>
                    <#if (f.statusId!'') == 'MFG_FLAG_ACTIVE'>
                        <form method="post" action="/mfg/control/buyerFlagSave" style="display:inline;">
                            <input type="hidden" name="receiverMobile" value="${f.receiverMobile!}"/>
                            <input type="hidden" name="buyerNick" value="${f.buyerNick!}"/>
                            <input type="hidden" name="riskLevel" value="${f.riskLevel!}"/>
                            <input type="hidden" name="statusId" value="MFG_FLAG_RELEASED"/>
                            <input type="submit" value="解除"/>
                        </form>
                    <#else>
                        <form method="post" action="/mfg/control/buyerFlagSave" style="display:inline;">
                            <input type="hidden" name="receiverMobile" value="${f.receiverMobile!}"/>
                            <input type="hidden" name="buyerNick" value="${f.buyerNick!}"/>
                            <input type="hidden" name="riskLevel" value="${f.riskLevel!}"/>
                            <input type="hidden" name="statusId" value="MFG_FLAG_ACTIVE"/>
                            <input type="submit" value="恢复"/>
                        </form>
                    </#if>
                </td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">名单为空。</p>
</#if>
<@pg.pager action="/mfg/control/buyers" query=('keyword=' + ((fl.keyword)!'')?url + '&' + 'riskLevel=' + ((parameters.riskLevel)!'')?url + '&' + 'statusId=' + ((parameters.statusId)!'')?url)
    page=(fl.page)!1 pageCount=(fl.pageCount)!1 total=(fl.totalCount)!0 unit="条" pageSize=(fl.pageSize)!20/>

<h2>售后历史汇总（建议名单用）</h2>
<p class="hint">
    按买家昵称聚合售后单：单数 ≥ ${3} 或退款 ≥ 500 元建议「观察」，单数 ≥ ${5} 或退款 ≥ 1000 元建议「人工复核」。
    系统只给建议，不会自动拉黑。
</p>
<#if buyers?has_content>
    <table>
        <tr><th>买家昵称</th><th class="num">售后单数</th><th class="num">退款合计</th><th>最后收件日</th>
            <th>建议等级</th><th>已在名单</th></tr>
        <#list buyers as b>
            <tr>
                <td>${b.buyerNick!}</td>
                <td class="num">${(b.claimCount)!0}</td>
                <td class="num"><#if b.refundTotal??>${b.refundTotal?string("0.00")}<#else>0.00</#if></td>
                <td><#if b.lastClaimDate??>${b.lastClaimDate?string("yyyy-MM-dd")}<#else>-</#if></td>
                <td><@levelLabel (b.suggestedRiskLevel)!''/></td>
                <td><#if (b.flagged!'N') == 'Y'><span class="ok">是</span><#else><span class="hint">否</span></#if></td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有带买家昵称的售后单。</p>
</#if>

