<#ftl encoding="UTF-8">
<#--
  库存变动原因的中文名。编号用的是 OFBiz 自带的 IID_REASON 清单
  （Inventory Item Detail Reason，见 ProductSeedData.xml），id 照抄，只在页面上换成中文。
  流水页与盘点页共用这一份，别在模板里各写一套。
-->
<#function label id>
    <#if (id!'') == 'VAR_LOST'><#return '丢失'>
    <#elseif (id!'') == 'VAR_STOLEN'><#return '被盗'>
    <#elseif (id!'') == 'VAR_FOUND'><#return '找到（盘盈）'>
    <#elseif (id!'') == 'VAR_DAMAGED'><#return '损坏'>
    <#elseif (id!'') == 'VAR_SAMPLE'><#return '样品 / 赠送'>
    <#elseif (id!'') == 'VAR_INTEGR'><#return '生产领用（组装）'>
    <#elseif (id!'') == 'VAR_MISSHIP_ORDERED'><#return '供方少发（应到）'>
    <#elseif (id!'') == 'VAR_MISSHIP_SHIPPED'><#return '我方多发（已发）'>
    <#else><#return '${id!}'>
    </#if>
</#function>
