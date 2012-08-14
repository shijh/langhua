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
 * @author Shi Yusen, shiys@langhua.cn 
 * 
 */
public class CmsSearchProperties {

	String query = "";
	String indexName = "";
	String displaypage = "";
	String searchPage = "";
	String sortName = "";
	/**String maxDateCreated = "";
	String maxDateLastModified = "";
	String minDateCreated = "";
	String minDateLastModified = "";*/
	List<String> fields = new ArrayList<String>();

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public String getIndexName() {
		return indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getDisplaypage() {
		return displaypage;
	}

	public void setDisplaypage(String displaypage) {
		this.displaypage = displaypage;
	}

	public String getSortName() {
		return sortName;
	}

	public void setSortName(String sortName) {
        if (sortName.equals("date-created")) {
        	this.sortName = "SORT_DATE_CREATED";
        } else if (sortName.equals("date-lastmodified")) {
        	this.sortName = "SORT_DATE_LASTMODIFIED";
        } else if (sortName.equals("title")) {
        	this.sortName = "SORT_TITLE";
        } else {
        	this.sortName = "SORT_DEFAULT";
        }
	}

	/**public String getMaxDateCreated() {
		return maxDateCreated;
	}

	public void setMaxDateCreated(String maxDateCreated) {
		this.maxDateCreated = maxDateCreated;
	}

	public String getMaxDateLastModified() {
		return maxDateLastModified;
	}

	public void setMaxDateLastModified(String maxDateLastModified) {
		this.maxDateLastModified = maxDateLastModified;
	}

	public String getMinDateCreated() {
		return minDateCreated;
	}

	public void setMinDateCreated(String minDateCreated) {
		this.minDateCreated = minDateCreated;
	}

	public String getMinDateLastModified() {
		return minDateLastModified;
	}

	public void setMinDateLastModified(String minDateLastModified) {
		this.minDateLastModified = minDateLastModified;
	}*/

	public List<String> getFields() {
		return fields;
	}

	public void setFileds(List<String> fields) {
		this.fields = fields;
	}

	public String getSearchPage() {
		return searchPage;
	}

	public void setSearchPage(String searchPage) {
		this.searchPage = searchPage;
	}

}
