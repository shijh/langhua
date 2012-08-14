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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.lucene.search.SortField;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.dom4j.Element;
import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.main.CmsException;
import org.opencms.util.CmsStringUtil;
import org.opencms.xml.CmsXmlException;
import org.opencms.xml.content.CmsXmlContent;
import org.opencms.xml.content.CmsXmlContentFactory;
import org.opencms.xml.types.I_CmsXmlContentValue;

/**
 * 
 * @author WangRui, wangrui@langhua.cn 
 * @author Shi Yusen, shiys@langhua.cn
 * 
 * @since version 1.0
 */

public class CmsSearchPipelineConfiguration implements I_CmsSearchConfiguration{
	
    private int m_displayPages;
	   
	private int m_matchesPerPage;
	   
	private List<SortField> m_sortFieldList;
	   
	private Map<String, CmsSearchFilter> m_filterMap;
	   
	private String m_defaultFieldName;
	   
	private String m_searchIndex;
	
	public static final String m_dateString = "\\[[0-9]+ TO [0-9]+\\]";
		
	public CmsSearchPipelineConfiguration(CmsObject cmso, String configFilePath){
	   init(cmso, configFilePath);
	}
	
	public void init(CmsObject cmso, String configFilePath) {
		m_sortFieldList = new ArrayList<SortField>();
		m_filterMap = new HashMap<String, CmsSearchFilter>();
		Locale locale = cmso.getRequestContext().getLocale();
		CmsFile file = null;
		try {
			file = cmso.readFile(configFilePath);
		} catch (CmsException e) {
			e.printStackTrace();
			return;
		}
		CmsXmlContent xmlContent = null;
		try {
			xmlContent = CmsXmlContentFactory.unmarshal(
					cmso, file);
		} catch (CmsXmlException e) {
			e.printStackTrace();
			return;
		}
		m_searchIndex = xmlContent.getStringValue(cmso, "indexname", locale);
		String displaypages = xmlContent.getStringValue(cmso, "displaypage", locale);
		if(CmsStringUtil.isNotEmptyOrWhitespaceOnly(displaypages)){
			m_displayPages = Integer.valueOf(displaypages);
		}
		String matchesperpage = xmlContent.getStringValue(cmso, "matchesperpage", locale);
		if(CmsStringUtil.isNotEmptyOrWhitespaceOnly(matchesperpage)){
			m_matchesPerPage = Integer.valueOf(matchesperpage);
		}
		m_defaultFieldName = xmlContent.getStringValue(cmso, "defaultfield", locale);
		
		/**
		 * Get the 1st and 2nd level search filters.
		 */
		List<I_CmsXmlContentValue> filterList = xmlContent.getValues("narrow", locale);
		for (int i=0; i<filterList.size(); i++) {
			I_CmsXmlContentValue contentValue = filterList.get(i);
			CmsSearchFilter filter = new CmsSearchFilter();
			Element element = contentValue.getElement();
			String filterName = element.selectSingleNode("narrowname").getStringValue();
			filter.setFilterName(filterName);
			filter.setParentFilterName(null);
			filter.setLevelQuery(null);
			List<Element> firstFilter = element.elements("queryname");
			List<String> firstFilters = new ArrayList();
			for(int m =0; m<firstFilter.size(); m++){
			   Element 	firElement = firstFilter.get(m);
			   String firstQueryName = firElement.selectSingleNode("queryname").getStringValue();
			   firstFilters.add(firstQueryName);
			   List<Element> subFilterList = firElement.elements("narrowcontent");
				for (int j=0; j<subFilterList.size(); j++) {
					Element subElement = subFilterList.get(j);
					CmsSearchFilter subFilter = new CmsSearchFilter();
					String subFilterName = subElement.element("narrowcontentname").getStringValue();
					subFilter.setFilterName(subFilterName);
					subFilter.setParentFilterName(filterName);
					Perl5Compiler compiler = new Perl5Compiler();
					Perl5Matcher matcher = new Perl5Matcher();
					try {
						if(matcher.contains(firstQueryName, compiler.compile(m_dateString))){
							subFilter.setLevelQuery(filterName + ":" + firstQueryName);	
						}else{
							subFilter.setLevelQuery(filterName + ":'" + firstQueryName + "'");
						}
					} catch (MalformedPatternException e) {
					}
					
					List<Element> subFilterConditions = subElement.elements("querycontentname");
					subFilter.setFilterConditions(getFilterConditions(subFilterName, subFilterConditions));
					List<CmsSearchFilter> subFilters = filter.getSubFilters();
					if(subFilters == null){
						subFilters = new ArrayList<CmsSearchFilter>();
					}
					subFilters.add(subFilter);
					filter.setSubFilters(subFilters);
					m_filterMap.put(subFilterName, subFilter);
				}			  		   
			}
			// set filter conditions
			filter.setFilterConditions(getFilterStringConditions(filterName, firstFilters));
			m_filterMap.put(filterName, filter);			
		}
		
		/**
		 * Get sort field.
		 */
		List<I_CmsXmlContentValue> sortList = xmlContent.getValues("sort", locale);
		for (int i=0; i<sortList.size(); i++) {
			I_CmsXmlContentValue contentValue = sortList.get(i);
			Element element = contentValue.getElement();
			List<Element> sortContents = element.elements("sortcontent");
			for (int j=0; j<sortContents.size(); j++) {
				Element sortNameElement = sortContents.get(j);
				String sortName = sortNameElement.element("sortname").getStringValue();
				String sortType = sortNameElement.element("sortfangshi").getStringValue();
				m_sortFieldList.add(new SortField(sortName, Integer.parseInt(sortType)));
			}
		}
	}

	protected List<CmsFilterCondition> getFilterStringConditions(String filterName, List filterConditions) {
		List<CmsFilterCondition> results = new ArrayList<CmsFilterCondition>();
		for (int j=0; j<filterConditions.size(); j++) {
			//Element element = filterConditions.get(j);
			CmsFilterCondition condition = new CmsFilterCondition();
			String content = (String)filterConditions.get(j);
			condition.setContent(content);
			Perl5Compiler compiler = new Perl5Compiler();
			Perl5Matcher matcher = new Perl5Matcher();
			try {
				if(matcher.contains(content, compiler.compile(m_dateString))){
					condition.setQueryString(" AND " + filterName + ":" + content);
				}else{
				  condition.setQueryString(" AND " + filterName + ":'" + content + "'");
				}
			} catch (MalformedPatternException e) {
			}
			results.add(condition);
		}
		return results;
	}
	
	protected List<CmsFilterCondition> getFilterConditions(String filterName, List<Element> filterConditions) {
		List<CmsFilterCondition> results = new ArrayList<CmsFilterCondition>();
		for (int j=0; j<filterConditions.size(); j++) {
			Element element = filterConditions.get(j);
			CmsFilterCondition condition = new CmsFilterCondition();
			String content = element.getStringValue();
			condition.setContent(content);
			Perl5Compiler compiler = new Perl5Compiler();
			Perl5Matcher matcher = new Perl5Matcher();
			try {
				if(matcher.contains(content, compiler.compile(m_dateString))){
					condition.setQueryString(" AND " + filterName + ":" + content);
				}else{
				  condition.setQueryString(" AND " + filterName + ":'" + content + "'");
				}
			} catch (MalformedPatternException e) {
			}
			results.add(condition);
		}
		return results;
	}

	public String getDefaultFieldName() {
		
		return m_defaultFieldName;
	}

	public Map<String, CmsSearchFilter> getFilterMap() {
		if (m_filterMap == null) {
			m_filterMap = new HashMap<String, CmsSearchFilter>();
		}
		return m_filterMap;
	}

	public int getMatchesPerPage() {
		return m_matchesPerPage;
	}

	public String getSearchIndex() {
		return m_searchIndex;
	}

	public List<SortField> getSortFieldList() {
		if (m_sortFieldList == null) {
			m_sortFieldList = new ArrayList<SortField>();
		}
		return m_sortFieldList;
	}

	public int getDisplayPages() {
		return m_displayPages;
	}

}
