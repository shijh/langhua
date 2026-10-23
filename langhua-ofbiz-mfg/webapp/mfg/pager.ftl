<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
    One pager for the list pages: where you are, the pages around the current one, first / last,
    and a page size chooser that keeps the filter of the page.

    action   the page, e.g. /mfg/control/receipts
    query    the filter of the page, already url escaped and without page / pageSize,
             e.g. search=abc&statusId=MFG_CREATED (may be empty)
    page     the current page, 1 based
    pageCount how many pages there are
    total    how many rows there are
    unit     the word after the number, 张 / 条 / 家
    pageSize how many rows one page holds
-->
<#macro pager action query page pageCount total unit="条" pageSize=20>
    <#-- the filters come in as name=value pairs; the empty ones are dropped so the links stay short -->
    <#assign kept = []>
    <#list (query!'')?split('&') as pair>
        <#if pair?has_content && pair?contains('=') && (pair?keep_after('='))?has_content>
            <#assign kept = kept + [pair]>
        </#if>
    </#list>
    <#assign clean = kept?join('&')>
    <#assign sep = '?'><#if clean?has_content><#assign sep = '?' + clean + '&'></#if>
    <#assign current = (page)!1><#if current lt 1><#assign current = 1></#if>
    <#assign pages = (pageCount)!1><#if pages lt 1><#assign pages = 1></#if>
    <#assign from = current - 2><#if from lt 1><#assign from = 1></#if>
    <#assign to = from + 4><#if to gt pages><#assign to = pages></#if>
    <#assign from = to - 4><#if from lt 1><#assign from = 1></#if>
    <p class="pager" data-page="${current}" data-pages="${pages}" data-total="${(total)!0}">
        <span class="pager-info">第 <strong>${current}</strong> / ${pages} 页，共 ${(total)!0} ${unit}</span>
        <#if pages gt 1>
            <#if current gt 1>
                <a href="${action}${sep}page=1" data-page-link="1">首页</a>
                <a href="${action}${sep}page=${current - 1}" data-page-link="${current - 1}">上一页</a>
            <#else>
                <span class="disabled">首页</span><span class="disabled">上一页</span>
            </#if>
            <#if (from gt 1)><span class="gap">…</span></#if>
            <#list from..to as n>
                <#if n == current><strong class="current" data-page-link="${n}">${n}</strong>
                <#else><a href="${action}${sep}page=${n}" data-page-link="${n}">${n}</a></#if>
            </#list>
            <#if (to lt pages)><span class="gap">…</span></#if>
            <#if current lt pages>
                <a href="${action}${sep}page=${current + 1}" data-page-link="${current + 1}">下一页</a>
                <a href="${action}${sep}page=${pages}" data-page-link="${pages}">末页</a>
            <#else>
                <span class="disabled">下一页</span><span class="disabled">末页</span>
            </#if>
        </#if>
        <span class="pager-size">每页
            <#list [20, 50, 100] as s>
                <#if s == pageSize><strong>${s}</strong>
                <#else><a href="${action}${sep}pageSize=${s}&amp;page=1" data-page-size="${s}">${s}</a></#if>
            </#list>
        </span>
    </p>
</#macro>
