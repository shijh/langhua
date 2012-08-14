/*
 * This library is part of OpenCms-SearchPipeline module of Langhua
 *
 * Copyright (C) 2009 Langhua Opensource Foundation (http://langhua.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For the latest version about this module, please see the
 * project website: http://langhua.org/portal/portal/default/OpenCms
 * 
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * For further information about Lucene, please see the
 * project website: http://lucene.apache.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.opencms.search.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.PageContext;

import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.opencms.jsp.CmsJspActionElement;
import org.opencms.main.OpenCms;
import org.opencms.search.CmsSearch;
import org.opencms.search.CmsSearchManager;
import org.opencms.search.CmsSearchParameters;
import org.opencms.search.CmsSearchResult;
import org.opencms.util.CmsStringUtil;

/**
 * 
 * @author WangRui, wangrui@langhua.cn
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public class CmsSearchPipeline extends CmsJspActionElement implements
		I_CmsSearchPipeline {

	public static final String PARAM_QUERY = "query";

	public static final String PARAM_PAGE = "searchPage";

	public static final String PARAM_SORT = "sort";

	public static final String PARAM_SEQUENCE = "sequence";

	public I_CmsSearchConfiguration m_configuration;

	public List<CmsSearchResult> m_searchResults;

	public long m_maxDateLong = 9223372036854775807L;

	public long m_minDateLong = -9223372036854775808L;

	public SortField m_sortField;

	public int m_currentPageNumber;

	public int m_searchResultTotalPages;

	protected CmsSearchParameters m_searchParams;

	public int m_searchResultCount;

	public List<CmsSearchFilter> m_filterList;

	public static final String SEARCHFORM = "document.searchform.";

	public static final String SEARCHEMPTYVALUE = ".value=\"\";";

	public static final String SEARCHMETNOD = "";

	public static final String FUNCTION = "function";

	public static final String LEFTKUOHAO = "{";

	public static final String RIGHTKUOHAO = "}";

	public static final String KUOHAO = "()";

	public static final String SEARCHFORMSUBMIT = "document.searchform.submit();";

	public static final String LEFTSINGLE = "'";

	public CmsSearchPipeline(String path) {
		super();
		m_searchResults = new ArrayList<CmsSearchResult>();
		m_filterList = new ArrayList<CmsSearchFilter>();
		m_currentPageNumber = 1;
		m_sortField = null;
		m_searchResultTotalPages = 0;
		m_configuration = new CmsSearchPipelineConfiguration(getCmsObject(),
				path);
	}

	public CmsSearchPipeline(PageContext context, HttpServletRequest req,
			HttpServletResponse res, String path) {
		super(context, req, res);
		m_searchResults = new ArrayList<CmsSearchResult>();
		m_filterList = new ArrayList<CmsSearchFilter>();
		m_currentPageNumber = 1;
		m_sortField = null;
		m_searchResultTotalPages = 0;
		m_configuration = new CmsSearchPipelineConfiguration(getCmsObject(),
				path);
	}

	public int getCurrentPageNumber() {
		return m_currentPageNumber;
	}

	public SortField getCurrentSortField() {
		return m_sortField;
	}

	public List<CmsSearchFilter> getFilterList() {
		m_filterList = new ArrayList<CmsSearchFilter>();
		String query = getRequest().getParameter(PARAM_QUERY);
		if (CmsStringUtil.isEmptyOrWhitespaceOnly(query))
			return m_filterList;
		query = getQueryContent(query);
		Map<String, CmsSearchFilter> filterMap = m_configuration.getFilterMap();
		String[] fieldQuerys = CmsStringUtil.splitAsArray(query, " AND ");
		for (int i = 0; i < fieldQuerys.length; i++) {
			String fieldQuery = fieldQuerys[i];
			String[] queryParts = CmsStringUtil.splitAsArray(fieldQuery, ':');
			String fieldName = getSearchConfiguration().getDefaultFieldName();
			if (queryParts.length > 1
					&& fieldQuery.startsWith(queryParts[0] + ":")) {
				// use default field
				fieldName = queryParts[0];
			}
			CmsSearchFilter filter = filterMap.get(fieldName);
			if (filter != null && filter.getSubFilters() != null
					&& filter.getSubFilters().size() > 0) {
				// we find the available 2nd level filters, so return the filter
				// list
				List<CmsSearchFilter> list = filter.getSubFilters();
				// m_filterList = filter.getSubFilters();
				for (int j = 0; j < list.size(); j++) {
					CmsSearchFilter filterLevel = list.get(j);
					String levelQuery = filterLevel.getLevelQuery();
					String filedName = filterLevel.getFilterName();
					boolean flag = false;
					String linkString = filedName + ":";
					flag = query.contains(linkString);
					if (!CmsStringUtil.isEmpty(levelQuery) && !flag) {
						if (query.contains(levelQuery)) {
							m_filterList.add(filterLevel);
						}
					}
				}
				return fillResultCount(m_filterList, query);
			}
		}

		// no, we don't find sub filters, use the default 1st level filter(s)
		Iterator<String> keys = filterMap.keySet().iterator();
		while (keys.hasNext()) {
			String key = keys.next();
			CmsSearchFilter filter = filterMap.get(key);
			// a 1st level filter doesn't have a parent and doesn't click on the
			// link
			boolean flag = false;
			String linkString = key + ":";
			flag = query.contains(linkString);
			if (CmsStringUtil.isEmpty(filter.getParentFilterName()) && !flag) {
				m_filterList.add(filter);
			}
		}
		return fillResultCount(m_filterList, query);
	}

	/**
	 * Fill search result count into every filter condition of search filters.
	 * 
	 * @param list
	 *            a list of CmsSearchFilter
	 * @param query
	 *            the query string
	 * @return the list of CmsSearchFilter
	 */
	private List<CmsSearchFilter> fillResultCount(List<CmsSearchFilter> list,
			String query) {
		CmsSearch search = new CmsSearch();
		search.setIndex(m_configuration.getSearchIndex());
		search.init(getCmsObject());
		for (int i = 0; i < list.size(); i++) {
			CmsSearchFilter filter = list.get(i);
			List<CmsFilterCondition> conditions = filter.getFilterConditions();
			for (int j = 0; j < conditions.size(); j++) {
				CmsFilterCondition condition = conditions.get(j);
				String conditionQuery = query + condition.getQueryString();
				condition.setQueryString(conditionQuery);
				search.setQuery(conditionQuery);
				search.getSearchResult();
				condition.setResultCount(search.getSearchResultCount());
			}
		}
		return list;
	}

	public I_CmsSearchConfiguration getSearchConfiguration() {
		return m_configuration;
	}

	public int getSearchResultTotalPages() {
		return m_searchResultTotalPages;
	}

	public List<CmsSearchResult> getSearchResultsList() {

		CmsSearch search;
		CmsSearchParameters searchParameters = getCmsSearchParameters();
		if (searchParameters != null) {
			search = new CmsSearch();
			search.init(getCmsObject());
			String queryContent = getRequest().getParameter(PARAM_QUERY);
			queryContent = getQueryContent(queryContent);
			String searchPage = getRequest().getParameter(PARAM_PAGE);
			if (searchPage == null || searchPage.equals("")
					|| searchPage.equals("null")) {
				searchPage = "1";
			}
			m_currentPageNumber = Integer.valueOf(searchPage);
			if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(queryContent)) {
				searchParameters.setQuery(queryContent);
				searchParameters.setSearchPage(Integer.valueOf(searchPage));
				search.setParameters(searchParameters);
				search.setIndex(m_configuration.getSearchIndex());
				search.setMatchesPerPage(Integer.valueOf(m_configuration
						.getMatchesPerPage()));
				search.setDisplayPages(Integer.valueOf(m_configuration
						.getDisplayPages()));
				m_searchResults = search.getSearchResult();
				m_searchParams = search.getParameters();
				m_searchResultCount = search.getSearchResultCount();
				Sort sort = search.getSortOrder();
				if (sort != null) {
					m_sortField = sort.getSort()[0];
				}
				if (m_searchResults != null) {
					int matchesPerPage = m_configuration.getMatchesPerPage();
					if (matchesPerPage != 0) {
						if (m_searchResultCount % matchesPerPage == 0) {
							m_searchResultTotalPages = m_searchResultCount
									/ matchesPerPage;
						} else {
							m_searchResultTotalPages = (m_searchResultCount / matchesPerPage) + 1;
						}
					}
				}
			}
		}

		return m_searchResults;
	}

	public String getSearchTimeConsumed() {
		double useTime = 0.001 * ((new Date()).getTime() - getRequestContext()
				.getRequestTime());
		double reserveBit = 3.0;
		double tempD = useTime * Math.pow(10.0, reserveBit);
		return (String.valueOf((Math.round(tempD))
				/ (Math.pow(10.0, reserveBit))));
	}

	public CmsSearchProperties getCmsSearchProperties() {
		CmsSearchProperties properties = new CmsSearchProperties();
		String sort = getRequest().getParameter(PARAM_SORT);
		if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(sort)) {
			properties.setSortName(sort);
		}
		/**
		 * String maxDateCreated = getRequest().getParameter("maxcreatedate");
		 * if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(maxDateCreated)) {
		 * properties.setMaxDateCreated(maxDateCreated); } String
		 * maxDateLastModified = getRequest().getParameter("maxlastdate"); if
		 * (CmsStringUtil.isNotEmptyOrWhitespaceOnly(maxDateLastModified)) {
		 * properties.setMaxDateLastModified(maxDateLastModified); } String
		 * minDateCreated = getRequest().getParameter("mincreatedate"); if
		 * (CmsStringUtil.isNotEmptyOrWhitespaceOnly(minDateCreated)) {
		 * properties.setMinDateCreated(minDateCreated); } String
		 * minDateLastModified = getRequest().getParameter("minlastdate"); if
		 * (CmsStringUtil.isNotEmptyOrWhitespaceOnly(minDateLastModified)) {
		 * properties.setMinDateLastModified(minDateLastModified); }
		 */
		List<String> list = getAllFieldsList();
		if (list != null && list.size() != 0) {
			properties.setFileds(list);
		}
		return properties;
	}

	public List<String> getAllFieldsList() {
		List<String> list = new ArrayList<String>();
		Map<String, CmsSearchFilter> filterMap = getSearchConfiguration()
				.getFilterMap();
		Iterator<String> i = filterMap.keySet().iterator();
		while (i.hasNext()) {
			CmsSearchFilter searchFilter = (CmsSearchFilter) filterMap.get(i
					.next());
			String filterName = searchFilter.getFilterName();
			if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(filterName)) {
				list.add(filterName);
			}
		}
		return list;
	}

	public List<String> getFirstFieldsList() {
		List<String> list = new ArrayList<String>();
		Map<String, CmsSearchFilter> filterMap = getSearchConfiguration()
				.getFilterMap();
		Iterator<String> keys = filterMap.keySet().iterator();
		while (keys.hasNext()) {
			String key = keys.next();
			CmsSearchFilter filter = filterMap.get(key);
			if (filter.getSubFilters().size() < 0) {
				list.add(filter.getFilterName());
			}
		}
		return list;
	}

	private CmsSearchParameters getCmsSearchParameters() {
		m_searchParams = new CmsSearchParameters();
		CmsSearchProperties properties = getCmsSearchProperties();
		Sort sort;
		if (properties != null) {
			List<String> fields = properties.getFields();
			fields.add(m_configuration.getDefaultFieldName());
			String sortName = properties.getSortName();
			/**
			 * String maxCreateDate = properties.getMaxDateCreated(); String
			 * minCreateDate = properties.getMinDateCreated(); String
			 * maxLateDate = properties.getMaxDateLastModified(); String
			 * minLateDate = properties.getMinDateLastModified();
			 */
			long maxCreateDateLong = m_maxDateLong;
			long minCreateDateLong = m_minDateLong;
			long maxLateDateLong = m_maxDateLong;
			long minLateDateLong = m_minDateLong;
			/**
			 * try { if
			 * (CmsStringUtil.isNotEmptyOrWhitespaceOnly(maxCreateDate)) {
			 * maxCreateDateLong = CmsCalendarWidget.getCalendarDate(
			 * org.opencms.workplace.Messages.get().getBundle(), maxCreateDate,
			 * true); } if
			 * (CmsStringUtil.isNotEmptyOrWhitespaceOnly(minCreateDate)) {
			 * minCreateDateLong = CmsCalendarWidget.getCalendarDate(
			 * org.opencms.workplace.Messages.get().getBundle(), minCreateDate,
			 * true); } if
			 * (CmsStringUtil.isNotEmptyOrWhitespaceOnly(maxLateDate)) {
			 * maxLateDateLong = CmsCalendarWidget.getCalendarDate(
			 * org.opencms.workplace.Messages.get().getBundle(), maxLateDate,
			 * true); } if
			 * (CmsStringUtil.isNotEmptyOrWhitespaceOnly(minLateDate)) {
			 * minLateDateLong = CmsCalendarWidget.getCalendarDate(
			 * org.opencms.workplace.Messages.get().getBundle(), minLateDate,
			 * true); } } catch (ParseException e) { }
			 */
			m_searchParams = new CmsSearchParameters();
			if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(sortName)) {
				m_searchParams.setSortName(sortName);
				sort = m_searchParams.getSort();
			} else {
				sort = CmsSearchParameters.SORT_DEFAULT;
			}

			if (isIndexExist()) {
				m_searchParams.setFields(fields);
				m_searchParams.setIndex(m_configuration.getSearchIndex());
				m_searchParams.setSort(sort);
				m_searchParams.setMatchesPerPage(Integer
						.valueOf(m_configuration.getMatchesPerPage()));
				m_searchParams.setMaxDateCreated(maxCreateDateLong);
				m_searchParams.setMaxDateLastModified(maxLateDateLong);
				m_searchParams.setMinDateCreated(minCreateDateLong);
				m_searchParams.setMinDateLastModified(minLateDateLong);
				m_searchParams.setCalculateCategories(false);
				m_searchParams.setCategories(Arrays.asList(new String[] {}));
				m_searchParams.setDisplayPages(Integer.valueOf(m_configuration
						.getDisplayPages()));
				m_searchParams.setExcerptOnlySearchedFields(false);
				m_searchParams.setRoots(Arrays.asList(new String[] {}));
				m_searchParams.setSearchCategories("");
				m_searchParams.setSearchRoots("/");
			}
		}
		return m_searchParams;
	}

	private String getQueryContent(String query) {
		if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(query)) {
			if (!query.contains(":")) {
				Perl5Compiler compiler = new Perl5Compiler();
				Perl5Matcher matcher = new Perl5Matcher();
				try {
					if (matcher
							.contains(
									query,
									compiler
											.compile(CmsSearchPipelineConfiguration.m_dateString))) {
						query = m_configuration.getDefaultFieldName() + ":"
								+ query;
					} else {
						query = m_configuration.getDefaultFieldName() + ":"
								+ LEFTSINGLE + query + LEFTSINGLE;
					}
				} catch (MalformedPatternException e) {
				}

			}
		}
		return query;
	}

	public boolean isIndexExist() {
		boolean existIndex = false;
		String indexName = m_configuration.getSearchIndex();
		if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(indexName)) {
			CmsSearchManager cmsSearchManager = OpenCms.getSearchManager();
			List<String> indexList = cmsSearchManager.getIndexNames();
			if (indexList != null && indexList.size() != 0) {
				Iterator<String> iterator = indexList.iterator();
				while (iterator.hasNext()) {
					String indexsName = iterator.next();
					if (indexName.equals(indexsName)) {
						existIndex = true;
						break;
					}
				}
			}
		}
		return existIndex;
	}

	public int getSearchResultCount() {
		return m_searchResultCount;
	}

	public void setSearchTimeStart() {
		getRequestContext().setRequestTime((new Date()).getTime());
	}

	public CmsSearchParameters getSearchParameters() {
		return m_searchParams;
	}
}
