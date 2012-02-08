/* This library is part of Taglib OpenCms module of Langhua
 *
 * Copyright (C) 2008 Langhua Opensource (http://www.langhua.org)
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.opencms.misc;

import java.util.Comparator;



public class ComparatorCount implements Comparator{
	private static ComparatorCount comparatorCount = null;

	private ComparatorCount() {

	}

	public static synchronized ComparatorCount getInstance() {
		if (comparatorCount == null) {
			comparatorCount = new ComparatorCount();
			return comparatorCount;
		}
		return null;
	}

	
	public int compare(Object arg0, Object arg1) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	

}
