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

import org.opencms.search.CmsSearchParameters;

/**
 * 
 * @author WangRui, wangrui@langhua.cn
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public class CmsFilterCondition {
	
	public String content;
	
	public int resultCount;
	
	public String queryString;

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public int getResultCount() {
		return resultCount;
	}

	public void setResultCount(int resultCount) {
		this.resultCount = resultCount;
	}

	public String getQueryString() {
		return queryString;
	}

	public void setQueryString(String queryString) {
		this.queryString = queryString;
	}
	
	public CmsSearchParameters getSearchParameters(CmsSearchParameters searchParams) {
		CmsSearchParameters newSearchParams = searchParams;
		newSearchParams.setQuery(queryString);
		return newSearchParams;
	}

}
