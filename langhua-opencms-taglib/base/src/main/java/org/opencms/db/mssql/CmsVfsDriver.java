/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software GmbH, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.db.mssql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.opencms.db.CmsDbContext;
import org.opencms.db.CmsDbSqlException;
import org.opencms.db.CmsResourceState;
import org.opencms.db.generic.CmsSqlManager;
import org.opencms.file.CmsDataAccessException;
import org.opencms.file.CmsResource;
import org.opencms.util.CmsUUID;
import org.opencms.db.generic.Messages;

/**
 * MS SQL implementation of the VFS driver methods.<p>
 *
 * @since 6.0.0
 */
public class CmsVfsDriver extends org.opencms.db.generic.CmsVfsDriver {

    /**
     * @see org.opencms.db.I_CmsVfsDriver#initSqlManager(String)
     */
    @Override
    public org.opencms.db.generic.CmsSqlManager initSqlManager(String classname) {

        return CmsSqlManager.getInstance(classname);
    }

    // Added by Shi Jinghai, huaruhai@hotmail.com
	/**
	 * @see org.opencms.db.I_CmsVfsDriver#readTopLatestResourceTree(org.opencms.db.CmsDbContext,
	 *      CmsUUID, java.lang.String, int, CmsResourceState, long, long, long,
	 *      long, long, long, int, int)
	 */
	public List<CmsResource> readTopLatestResourceTree(CmsDbContext dbc, CmsUUID projectId,
			String parentPath, int type, CmsResourceState state,
			long lastModifiedAfter, long lastModifiedBefore,
			long releasedAfter, long releasedBefore, long expiredAfter,
			long expiredBefore, int mode, int top)
			throws CmsDataAccessException {

		List<CmsResource> result = new ArrayList<CmsResource>();
		StringBuffer conditions = new StringBuffer();
		List params = new ArrayList(5);

		//prepareProjectCondition(projectId, top, conditions, params);
		prepareResourceCondition(projectId, mode, conditions);
		prepareTypeCondition(projectId, type, mode, conditions, params);
		prepareTimeRangeCondition(projectId, lastModifiedAfter,
				lastModifiedBefore, conditions, params);
		prepareReleasedTimeRangeCondition(projectId, releasedAfter,
				releasedBefore, conditions, params);
		prepareExpiredTimeRangeCondition(projectId, expiredAfter,
				expiredBefore, conditions, params);
		preparePathCondition(projectId, parentPath, mode, conditions, params);
		prepareStateCondition(projectId, state, mode, conditions, params);

		ResultSet res = null;
		PreparedStatement stmt = null;
		Connection conn = null;

		try {
			conn = m_sqlManager.getConnection(dbc);
			StringBuffer queryBuf = new StringBuffer(256);
			queryBuf.append(m_sqlManager.readQuery(projectId,
					"C_MSSQL_RESOURCES_TOP_ORDER_BY_DATELASTMODIFIED"));
			queryBuf.append(conditions);
			queryBuf.append(" ");
			queryBuf.append(m_sqlManager.readQuery(projectId,
					"C_MSSQL_RESOURCES_TOP_ORDER_BY_DATELASTMODIFIED_1"));
			stmt = m_sqlManager.getPreparedStatementForSql(conn, queryBuf
					.toString());

			stmt.setInt(1, top);
			int i = 0;
			for (; i < params.size(); i++) {
				Object obj = params.get(i);
				if (obj instanceof String) {
					stmt.setString(i + 2, (String) obj);
				} else if (obj instanceof Integer) {
					stmt.setInt(i + 2, (Integer) obj);
				} else if (obj instanceof Long) {
					stmt.setLong(i + 2, ((Long) obj).longValue());
				}
			}

			res = stmt.executeQuery();
			while (res.next()) {
				CmsResource resource = createResource(res, projectId);
				result.add(resource);
			}

		} catch (SQLException e) {
			throw new CmsDbSqlException(Messages.get().container(
					Messages.ERR_GENERIC_SQL_1,
					CmsDbSqlException.getErrorQuery(stmt)), e);
		} finally {
			m_sqlManager.closeAll(dbc, conn, stmt, res);
		}

		return result;
	}

	/**
	 * @see org.opencms.db.I_CmsVfsDriver#readPagedLatestResourceTree(org.opencms.db.CmsDbContext,
	 *      CmsUUID, java.lang.String, int, CmsResourceState, long, long, long,
	 *      long, long, long, int, int, int)
	 */
	public List<CmsResource> readPagedLatestResourceTree(CmsDbContext dbc,
			CmsUUID projectId, String parentPath, int type,
			CmsResourceState state, long lastModifiedAfter,
			long lastModifiedBefore, long releasedAfter, long releasedBefore,
			long expiredAfter, long expiredBefore, int mode, int startRow,
			int endRow) throws CmsDataAccessException {

		List<CmsResource> result = new ArrayList<CmsResource>();

		StringBuffer conditions = new StringBuffer();
		List params = new ArrayList(5);

		// prepare the selection criteria
		prepareProjectCondition(projectId, mode, conditions, params);
		prepareResourceCondition(projectId, mode, conditions);
		prepareTypeCondition(projectId, type, mode, conditions, params);
		prepareTimeRangeCondition(projectId, lastModifiedAfter,
				lastModifiedBefore, conditions, params);
		prepareReleasedTimeRangeCondition(projectId, releasedAfter,
				releasedBefore, conditions, params);
		prepareExpiredTimeRangeCondition(projectId, expiredAfter,
				expiredBefore, conditions, params);
		preparePathCondition(projectId, parentPath, mode, conditions, params);
		prepareStateCondition(projectId, state, mode, conditions, params);

		// now read matching resources within the subtree
		ResultSet res = null;
		PreparedStatement stmt = null;
		Connection conn = null;

		try {
			conn = m_sqlManager.getConnection(dbc);
			StringBuffer queryBuf = new StringBuffer(256);
			queryBuf.append(m_sqlManager.readQuery(projectId,
					"C_MSSQL_RESOURCES_PAGED_ORDER_BY_DATELASTMODIFIED"));
			queryBuf.append(conditions);
			queryBuf.append(" ");
			queryBuf.append(m_sqlManager.readQuery(projectId,
					"C_MSSQL_RESOURCES_PAGED_ORDER_BY_DATELASTMODIFIED_2"));
			queryBuf.append(conditions);
			queryBuf.append(" ");
			queryBuf.append(m_sqlManager.readQuery(projectId,
					"C_MSSQL_RESOURCES_PAGED_ORDER_BY_DATELASTMODIFIED_3"));

			stmt = m_sqlManager.getPreparedStatementForSql(conn, queryBuf
					.toString());
			stmt.setInt(1, endRow);

			int i = 0;
			for (; i < params.size(); i++) {
				Object obj = params.get(i);
				if (obj instanceof String) {
					stmt.setString(i + 2, (String) obj);

				} else if (obj instanceof Integer) {
					stmt.setInt(i + 2, (Integer) obj);

				} else if (obj instanceof Long) {
					stmt.setLong(i + 2, ((Long) obj).longValue());

				}
			}

			stmt.setInt(i + 2, startRow);
			int j = i;
			for (; j - i < params.size(); j++) {

				Object obj = params.get(j - i);
				if (obj instanceof String) {
					stmt.setString(j + 3, (String) obj);

				} else if (obj instanceof Integer) {
					stmt.setInt(j + 3, (Integer) obj);

				} else if (obj instanceof Long) {
					stmt.setLong(j + 3, ((Long) obj).longValue());

				}
			}

			res = stmt.executeQuery();

			while (res.next()) {

				CmsResource resource = createResource(res, projectId);
				result.add(resource);

			}

		} catch (SQLException e) {
			throw new CmsDbSqlException(Messages.get().container(
					Messages.ERR_GENERIC_SQL_1,
					CmsDbSqlException.getErrorQuery(stmt)), e);
		} finally {
			m_sqlManager.closeAll(dbc, conn, stmt, res);
		}

		return result;
	}

	/**
	 * @see org.opencms.db.I_CmsVfsDriver#readTopLatestResourceTreeByTypes(org.opencms.db.CmsDbContext,
	 *      CmsUUID, java.lang.String, List<Integer>, CmsResourceState, long, long, long,
	 *      long, long, long, int, int)
	 */
	public List<CmsResource> readTopLatestResourceTreeByTypes(CmsDbContext dbc,
			CmsUUID projectId, String parent, List<Integer> types,
			CmsResourceState state, long startTime, long endTime,
			long releasedAfter, long releasedBefore, long expiredAfter,
			long expiredBefore, int mode, int top)
			throws CmsDataAccessException {
		
		
		List<CmsResource> result = new ArrayList<CmsResource>();
		StringBuffer conditions = new StringBuffer();
		List params = new ArrayList(5);

		
		prepareResourceCondition(projectId, mode, conditions);
		prepareTypesCondition(projectId, types, mode, conditions, params);
		prepareTimeRangeCondition(projectId, startTime,
				endTime, conditions, params);
		prepareReleasedTimeRangeCondition(projectId, releasedAfter,
				releasedBefore, conditions, params);
		prepareExpiredTimeRangeCondition(projectId, expiredAfter,
				expiredBefore, conditions, params);
		preparePathCondition(projectId, parent, mode, conditions, params);
		prepareStateCondition(projectId, state, mode, conditions, params);

		ResultSet res = null;
		PreparedStatement stmt = null;
		Connection conn = null;

		try {
			conn = m_sqlManager.getConnection(dbc);
			StringBuffer queryBuf = new StringBuffer(256);
			queryBuf.append(m_sqlManager.readQuery(projectId,
					"C_MSSQL_RESOURCES_TOP_ORDER_BY_DATELASTMODIFIED"));
			queryBuf.append(conditions);
			queryBuf.append(" ");
			queryBuf.append(m_sqlManager.readQuery(projectId,
					"C_MSSQL_RESOURCES_TOP_ORDER_BY_DATELASTMODIFIED_1"));
			stmt = m_sqlManager.getPreparedStatementForSql(conn, queryBuf
					.toString());
			stmt.setInt(1, top);
			int i = 0;
			for (; i < params.size(); i++) {
				Object obj = params.get(i);
				if (obj instanceof String) {
					stmt.setString(i + 2, (String) obj);
				} else if (obj instanceof Integer) {
					stmt.setInt(i + 2, (Integer) obj);
				} else if (obj instanceof Long) {
					stmt.setLong(i + 2, ((Long) obj).longValue());
				}
			}

			res = stmt.executeQuery();
			while (res.next()) {
				CmsResource resource = createResource(res, projectId);
				result.add(resource);
			}

		} catch (SQLException e) {
			throw new CmsDbSqlException(Messages.get().container(
					Messages.ERR_GENERIC_SQL_1,
					CmsDbSqlException.getErrorQuery(stmt)), e);
		} finally {
			m_sqlManager.closeAll(dbc, conn, stmt, res);
		}

		return result;
	}
}