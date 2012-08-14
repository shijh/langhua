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
import java.util.List;

/**
 * 
 * @author WangRui, wangrui@langhua.cn
 * 
 */
public class CmsSearchFilter {
	
	public List<CmsSearchFilter> subFilters;
	
	public String filterName;
	
	public String parentFilterName;
	
	public List<CmsFilterCondition> filterConditions;
	
	public String levelQuery;

	public String getFilterName() {
		return filterName;
	}

	public void setFilterName(String filterName) {
		this.filterName = filterName;
	}

	public List<CmsFilterCondition> getFilterConditions() {
		if (filterConditions == null)
			return new ArrayList<CmsFilterCondition>();
		return filterConditions;
	}

	public void setFilterConditions(List<CmsFilterCondition> filterConditions) {
		this.filterConditions = filterConditions;
	}
	
	public void setSubFilters(List<CmsSearchFilter> subFilters) {
		this.subFilters = subFilters;
	}
	
	public List<CmsSearchFilter> getSubFilters() {
		return subFilters;
	}
	
	public void setParentFilterName(String parentFilterName) {
		this.parentFilterName = parentFilterName;
	}
	
	public String getParentFilterName() {
		return parentFilterName;
	}

	public String getLevelQuery() {
		return levelQuery;
	}

	public void setLevelQuery(String levelQuery) {
		this.levelQuery = levelQuery;
	}
}