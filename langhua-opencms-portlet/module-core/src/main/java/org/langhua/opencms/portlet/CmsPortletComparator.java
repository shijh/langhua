/*
 * This library is part of OpenCms-Portlet module
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
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * For further information about OpenCms-Portlet, please see the 
 * project website: http://langhua.org/opencms/
 *  
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.opencms.portlet;

import java.util.Comparator;

import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.main.CmsException;

public class CmsPortletComparator implements Comparator<Object> {
	private CmsObject m_cmso;
    public CmsPortletComparator(CmsObject cmso){
    	m_cmso=cmso;
    }
	public int compare(Object o1, Object o2) {
		if ((o1 == o2) || !(o2 instanceof CmsResource) || !(o1 instanceof CmsResource)) {
            return 0;
        }
 		CmsResource r1 = (CmsResource)o1;
        CmsResource r2 = (CmsResource)o2;
        try {
			String title1=m_cmso.readPropertyObject(r1,"Title",true).getValue();
			String title2=m_cmso.readPropertyObject(r2,"Title",true).getValue();
	        int flag = title1.compareTo(title2);
	 		return flag;
		} catch (CmsException e) {
			return 0;
		}
	}
}
