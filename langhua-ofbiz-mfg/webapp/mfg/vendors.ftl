<#ftl encoding="UTF-8">
<#--
  Outsourcing vendors (SMT / PCB) with the shipping details used when material is couriered.
  Data: vendorList.vendors (each with shippingLabel)
-->
<#assign vl = (vendorList)!{}>
<#assign vendors = (vl.vendors)![]>
<#assign errList = (errorMessageList)![]>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>外协厂家 - Hardware Assembly</title>
    <style type="text/css">
        body { font-family: Arial, Helvetica, sans-serif; margin: 1.5em; color: #222; }
        h1 { font-size: 1.3em; } h2 { font-size: 1.05em; margin-top: 1.4em; }
        table { border-collapse: collapse; margin-top: .6em; }
        th, td { border: 1px solid #ccc; padding: 4px 10px; text-align: left; vertical-align: top; }
        th { background: #f3f3f3; }
        .num { text-align: right; }
        .nav a { margin-right: 1em; }
        .error { color: #b00000; font-weight: bold; }
        .hint { color: #666; font-size: .85em; }
        fieldset { border: 1px solid #ddd; padding: .6em 1em; margin-top: 1em; }
        textarea { font-family: Consolas, monospace; }
        .label { background: #fafafa; }
    </style>
</head>
<body>
<p class="nav">
    <a href="/mfg/control/main">工单</a>
    <a href="/mfg/control/products">产品与 BOM</a>
    <a href="/mfg/control/materials">物料批次</a>
    <a href="/mfg/control/delivery">出货单</a>
    <a href="/mfg/control/vendors"><b>外协厂家</b></a>
    <a href="/mfg/control/claims">售后单</a>
    <a href="/mfg/control/buyers">买家名单</a>
    <a href="/mfg/control/quality">质量指标</a>
    <a href="/mfg/control/shortage">齐套预警</a>
    <a href="/mfg/control/dashboard">看板</a>
    <a href="/mfg/control/sn">SN 查询</a>
</p>

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

<script type="text/javascript" src="/mfg/js/copyLabel.js"></script>
</body>
</html>
