<#ftl encoding="UTF-8">
<#import "pager.ftl" as pg>
<#setting url_escaping_charset="UTF-8">
<#--
  Outsourcing vendors (SMT / PCB) with the shipping details used when material is couriered.
  Data: vendorList.vendors (each with shippingLabel)
-->
<#assign vl = (vendorList)!{}>
<#assign vendors = (vl.vendors)![]>
<#assign errList = (errorMessageList)![]>

<h1>外协厂家（邮寄物料用）</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<fieldset>
    <legend>新增 / 修改厂家</legend>
    <form method="post" action="/mfg/control/vendorSave">
        <p>
            厂家编号 <input type="text" name="vendorId" size="12" placeholder="留空自动生成 V-yyMMdd-NN"/>
            厂家名称 <input type="text" name="vendorName" size="20" required="required"/>
            类型
            <select name="vendorType">
                <option value="SMT">SMT 贴片</option>
                <option value="PCB">PCB 制板</option>
                <option value="BOTH">制板 + 贴片</option>
            </select>
        </p>
        <p>
            收件人 <input type="text" name="contactName" size="12"/>
            手机号 <input type="text" name="contactMobile" size="14"/>
            客户编号 <input type="text" name="customerCode" size="14" placeholder="厂家给我们的编号"/>
        </p>
        <p>
            收货地址（可多行）
        </p>
        <textarea name="addressText" rows="3" cols="90" placeholder="广东省深圳市宝安区…"></textarea>
        <p>
            备注 <input type="text" name="note" size="40" placeholder="可选"/>
            <input type="submit" value="保存"/>
        </p>
    </form>
    <p class="hint">修改已有厂家：把「厂家编号」填上再保存即可覆盖。客户编号会打印在邮寄标签上。</p>
</fieldset>

<h2>厂家清单</h2>
<form method="get" action="/mfg/control/vendors">
    搜索 <input type="text" name="search" size="24" value="${(vl.search)!''}" placeholder="厂家 / 收件人 / 手机号 / 客户编号"/>
    <input type="submit" value="查询"/>
    <span class="hint">共 ${(vl.totalCount)!0} 家</span>
</form>
<#if vendors?has_content>
    <table>
        <tr><th>厂家编号</th><th>名称</th><th>类型</th><th>收件人</th><th>手机号</th>
            <th>客户编号</th><th>地址</th><th>邮寄标签（可直接复制）</th></tr>
        <#list vendors as v>
            <tr>
                <td>${v.vendorId!}</td>
                <td>${v.vendorName!}</td>
                <td>${v.vendorType!}</td>
                <td>${v.contactName!}</td>
                <td>${v.contactMobile!}</td>
                <td>${v.customerCode!}</td>
                <td>${v.addressText!}</td>
                <td class="label">
                    <textarea rows="4" cols="46" readonly="readonly">${v.shippingLabel!}</textarea>
                    <br/><button type="button" onclick="mfgCopyLabel(this)">复制标签</button>
                </td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有厂家记录。</p>
</#if>
<@pg.pager action="/mfg/control/vendors" query=('search=' + ((vl.search)!'')?url)
    page=(vl.page)!1 pageCount=(vl.pageCount)!1 total=(vl.totalCount)!0 unit="家" pageSize=(vl.pageSize)!20/>

<script type="text/javascript" src="/mfg/js/copyLabel.js"></script>

