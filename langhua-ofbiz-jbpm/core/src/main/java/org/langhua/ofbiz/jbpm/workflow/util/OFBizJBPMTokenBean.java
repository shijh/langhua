/*
 * This library is part of OFBiz-jBPM Component of Langhua
 *
 * Copyright (C) 2010  Langhua Opensource Foundation (http://langhua.org)
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
 * For the latest version about this component, please see the
 * project website: http://langhua.org/opensource/ofbiz/ofbiz-jbpm/
 * 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 * 
 * For more information on JBPM, please see the
 * project website: http://www.jboss.org/jbpm/
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.ofbiz.jbpm.workflow.util;

import java.util.Date;

/**
 * Token Bean Implementation.
 * 
 * @author David Loiseau
 * @author Shi Yusen
 * @author Shang Zhengwu,shangzw@langhua.cn
 * 
 */

public class OFBizJBPMTokenBean extends Object {

	long id;
	String name;
	String nodeName;
	String nodeClassName;
	Date start;
	Date end;
	long level;

	public OFBizJBPMTokenBean(long id, String name, String nodeName,
			String nodeClassName, Date start, Date end, long level) {

		this.id = id;
		this.name = name;
		this.nodeName = nodeName;
		this.nodeClassName = nodeClassName;
		this.start = start;
		this.end = end;
		this.level = level;
	}

	private String getTypeNameFromClassName(String className) {
		String typeName = "";
		if (className.indexOf(".") > 0) {
			typeName = className.substring(className.lastIndexOf(".") + 1);
		}
		return typeName;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLabel() {
		String label = "";
		int i = 1;
		while (i < this.level) {
			label = label + "---";
			i++;
		}
		if (i > 1)
			label = label + " ";
		label = label + this.name;

		return label;
	}

	public String getNodeName() {
		return nodeName;
	}

	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}

	public Date getEnd() {
		return end;
	}

	public void setEnd(Date end) {
		this.end = end;
	}

	public Date getStart() {
		return start;
	}

	public void setStart(Date start) {
		this.start = start;
	}

	public String getNodeType() {
		return getTypeNameFromClassName(this.nodeClassName);
	}

	public boolean isSignal() {
		if (this.end == null)
			return true;
		return false;
	}

	/**
	 * @see java.lang.Object#clone()
	 */
	public Object clone() {

		// create a copy of the jbpm token bean
		OFBizJBPMTokenBean result = new OFBizJBPMTokenBean(id, name, nodeName,
				nodeClassName, start, end, level);
		return result;
	}

}
