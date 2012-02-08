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

import java.util.List;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import org.opencms.main.OpenCms;

public class DataBaseConnection {
	
	private static final String SELECT_ALL = "SELECT * from LH_COUNTER";
	
	private static final String SQL = "SELECT * FROM LH_COUNTER WHERE LH_COUNTER.RESOURCE_ID IN (SELECT RESOURCE_ID FROM CMS_OFFLINE_RESOURCES) AND LH_COUNTER.PROJECTID < 0 ORDER BY COUNT DESC";
	
	private static final String NAME = "NAME";
	
	public List<LH_counterBeans> select() {
		List<LH_counterBeans> list = new ArrayList<LH_counterBeans>();
		Connection conn = null;
		PreparedStatement stmt = null;
		try {
			conn = OpenCms.getSqlManager().getConnection(
					OpenCms.getSqlManager().getDefaultDbPoolName());			
			stmt = conn.prepareStatement(SELECT_ALL);
			ResultSet resultSet = stmt.executeQuery();
			while (resultSet.next()) {
				LH_counterBeans lH_counterBeans = new LH_counterBeans();				
				lH_counterBeans.setName(resultSet.getString(NAME));				
				list.add(lH_counterBeans);
			}

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return list;
	}

	public List<LH_counterBeans> selectSqlSort() {
		List<LH_counterBeans> list = new ArrayList<LH_counterBeans>();
		Connection conn = null;
		PreparedStatement stmt = null;
		try {
			conn = OpenCms.getSqlManager().getConnection(
					OpenCms.getSqlManager().getDefaultDbPoolName());			
			stmt = conn.prepareStatement(SQL);
			ResultSet resultSet = stmt.executeQuery();
			while (resultSet.next()) {
				LH_counterBeans lH_counterBeans = new LH_counterBeans();				
				lH_counterBeans.setName(resultSet.getString(NAME));				
				list.add(lH_counterBeans);
			}

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return list;
	}

	public List<LH_counterBeans> sortList() {
		List<LH_counterBeans> list = select();
		ComparatorCount comparatorCount = ComparatorCount.getInstance();
		Collections.sort(list, comparatorCount);
		Collections.reverse(list);
		return list;
	}
}
