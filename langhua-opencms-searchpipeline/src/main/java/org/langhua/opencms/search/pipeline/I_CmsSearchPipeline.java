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

import java.util.List;

import org.apache.lucene.search.SortField;
import org.opencms.search.CmsSearchParameters;
import org.opencms.search.CmsSearchResult;

/**
 * The search pipeline interface.
 * 
 * Please note, the implement of this interface should be initialized with Locale parameter.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public interface I_CmsSearchPipeline {
	
	/**
	 * Get the main search parameters.
	 * The CmsSearchParameters may be initialed in getSearchResultsList. 
	 * 
	 * @return the CmsSearchParameters
	 */
	CmsSearchParameters getSearchParameters();
	
	/**
	 * Get a CmsSearchResultList accord to the query string, page, sort.
	 * 
	 * @return the CmsSearchResultList
	 */
	List<CmsSearchResult> getSearchResultsList();
	
	/**
	 * Get search time consumed, the return result should be in the format link 0.23seconds. 
	 * 
	 * @return the search time consumed
	 */
	String getSearchTimeConsumed();

	/**
	 * Set the time to start a search.
	 */
	void setSearchTimeStart();
	
	/**
	 * Get the total page number of the search result according to the configuration on results per page.
	 * 
	 * @return the total page number of the search result
	 */
	int getSearchResultTotalPages();
	
	/**
	 * Get the number of current page displayed.
	 * 
	 * @return the number of current page
	 */
	int getCurrentPageNumber();
	
    /**
     * Returns the total number of search results matching the query.<p>
     * 
     * @return the total number of search results matching the query
     */
	int getSearchResultCount();
	
	/**
	 * Get current sort field.
	 * 
	 * @return the sort field in current display
	 */
	SortField getCurrentSortField();
	
	/**
	 * Get a instance of search configuration 
	 * 
	 * @return the instance of search configuration
	 */
	I_CmsSearchConfiguration getSearchConfiguration();

	/**
	 * Get an available CmsSearchFilter list.
	 * 
	 * @return the search filter list
	 */
	List<CmsSearchFilter> getFilterList();
}
