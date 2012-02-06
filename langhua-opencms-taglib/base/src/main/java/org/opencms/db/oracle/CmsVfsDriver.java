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

package org.opencms.db.oracle;

import org.opencms.db.CmsDbContext;
import org.opencms.db.CmsDbEntryNotFoundException;
import org.opencms.db.CmsDbIoException;
import org.opencms.db.CmsDbSqlException;
import org.opencms.db.CmsResourceState;
import org.opencms.db.generic.CmsSqlManager;
import org.opencms.db.generic.Messages;
import org.opencms.file.CmsDataAccessException;
import org.opencms.file.CmsFolder;
import org.opencms.file.CmsProject;
import org.opencms.file.CmsResource;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsFileUtil;
import org.opencms.util.CmsUUID;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.dbcp.DelegatingResultSet;

/**
 * Oracle implementation of the VFS driver methods.<p>
 * 
 * @since 6.0.0 
 */
public class CmsVfsDriver extends org.opencms.db.generic.CmsVfsDriver {

    /**
     * @see org.opencms.db.I_CmsVfsDriver#createContent(CmsDbContext, CmsUUID, CmsUUID, byte[])
     */
    @Override
    public void createContent(CmsDbContext dbc, CmsUUID projectId, CmsUUID resourceId, byte[] content)
    throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, projectId, "C_ORACLE_OFFLINE_CONTENTS_WRITE");

            // first insert new file without file_content, then update the file_content
            // these two steps are necessary because of using BLOBs in the Oracle DB
            stmt.setString(1, resourceId.toString());

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, null);
        }

        // now update the file content
        internalWriteContent(dbc, projectId, resourceId, content, -1);
    }

    /**
     * @see org.opencms.db.generic.CmsVfsDriver#createOnlineContent(CmsDbContext, CmsUUID, byte[], int, boolean, boolean)
     */
    @Override
    public void createOnlineContent(
        CmsDbContext dbc,
        CmsUUID resourceId,
        byte[] contents,
        int publishTag,
        boolean keepOnline,
        boolean needToUpdateContent) throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = m_sqlManager.getConnection(dbc);
            boolean dbcHasProjectId = (dbc.getProjectId() != null) && !dbc.getProjectId().isNullUUID();
            if (needToUpdateContent || dbcHasProjectId) {
                if (dbcHasProjectId || !OpenCms.getSystemInfo().isHistoryEnabled()) {
                    // remove the online content for this resource id
                    stmt = m_sqlManager.getPreparedStatement(conn, "C_ONLINE_CONTENTS_DELETE");
                    stmt.setString(1, resourceId.toString());
                    stmt.executeUpdate();
                    m_sqlManager.closeAll(dbc, null, stmt, null);
                } else {
                    // put the online content in the history
                    stmt = m_sqlManager.getPreparedStatement(conn, "C_ONLINE_CONTENTS_HISTORY");
                    stmt.setString(1, resourceId.toString());
                    stmt.executeUpdate();
                    m_sqlManager.closeAll(dbc, null, stmt, null);
                }

                // create new empty online content entry
                stmt = m_sqlManager.getPreparedStatement(conn, "C_ORACLE_ONLINE_CONTENTS_WRITE");
                stmt.setString(1, resourceId.toString());
                stmt.setInt(2, publishTag);
                stmt.setInt(3, publishTag);
                stmt.setInt(4, keepOnline ? 1 : 0);
                stmt.executeUpdate();
                m_sqlManager.closeAll(dbc, conn, stmt, null);

                // now update the file content
                internalWriteContent(dbc, CmsProject.ONLINE_PROJECT_ID, resourceId, contents, publishTag);
            } else {
                // update old content entry                        
                stmt = m_sqlManager.getPreparedStatement(conn, "C_HISTORY_CONTENTS_UPDATE");
                stmt.setInt(1, publishTag);
                stmt.setString(2, resourceId.toString());
                stmt.executeUpdate();
                m_sqlManager.closeAll(dbc, null, stmt, null);

                if (!keepOnline) {
                    // put the online content in the history 
                    stmt = m_sqlManager.getPreparedStatement(conn, "C_ONLINE_CONTENTS_HISTORY");
                    stmt.setString(1, resourceId.toString());
                    stmt.executeUpdate();
                    m_sqlManager.closeAll(dbc, null, stmt, null);
                }
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, null);
        }
    }

    /**
     * @see org.opencms.db.I_CmsVfsDriver#initSqlManager(String)
     */
    @Override
    public org.opencms.db.generic.CmsSqlManager initSqlManager(String classname) {

        return CmsSqlManager.getInstance(classname);
    }

    /**
     * @see org.opencms.db.I_CmsVfsDriver#writeContent(CmsDbContext, CmsUUID, byte[])
     */
    @Override
    public void writeContent(CmsDbContext dbc, CmsUUID resourceId, byte[] content) throws CmsDataAccessException {

        internalWriteContent(dbc, dbc.currentProject().getUuid(), resourceId, content, -1);
    }

    /**
     * Writes the resource content with the specified resource id.<p>
     * 
     * @param dbc the current database context
     * @param projectId the id of the current project
     * @param resourceId the id of the resource used to identify the content to update
     * @param contents the new content of the file
     * @param publishTag the publish tag if to be written to the online content
     * 
     * @throws CmsDataAccessException if something goes wrong
     */
    protected void internalWriteContent(
        CmsDbContext dbc,
        CmsUUID projectId,
        CmsUUID resourceId,
        byte[] contents,
        int publishTag) throws CmsDataAccessException {

        PreparedStatement stmt = null;
        PreparedStatement commit = null;
        Connection conn = null;
        ResultSet res = null;

        boolean wasInTransaction = false;
        try {
            conn = m_sqlManager.getConnection(dbc);
            if (projectId.equals(CmsProject.ONLINE_PROJECT_ID)) {
                stmt = m_sqlManager.getPreparedStatement(conn, projectId, "C_ORACLE_ONLINE_CONTENTS_UPDATECONTENT");
            } else {
                stmt = m_sqlManager.getPreparedStatement(conn, projectId, "C_ORACLE_OFFLINE_CONTENTS_UPDATECONTENT");
            }

            wasInTransaction = !conn.getAutoCommit();
            if (!wasInTransaction) {
                conn.setAutoCommit(false);
            }

            // update the file content in the contents table
            stmt.setString(1, resourceId.toString());
            if (projectId.equals(CmsProject.ONLINE_PROJECT_ID)) {
                stmt.setInt(2, publishTag);
                stmt.setInt(3, publishTag);
            }
            res = ((DelegatingResultSet)stmt.executeQuery()).getInnermostDelegate();
            if (!res.next()) {
                throw new CmsDbEntryNotFoundException(Messages.get().container(
                    Messages.LOG_READING_RESOURCE_1,
                    resourceId));
            }
            // write file content 
            OutputStream output = CmsUserDriver.getOutputStreamFromBlob(res, "FILE_CONTENT");
            output.write(contents, 0, contents.length);
            output.close();

            if (!wasInTransaction) {
                commit = m_sqlManager.getPreparedStatement(conn, "C_COMMIT");
                commit.execute();
                m_sqlManager.closeAll(dbc, null, commit, null);
            }

            m_sqlManager.closeAll(dbc, null, stmt, res);

            // this is needed so the finally block works correctly
            commit = null;
            stmt = null;
            res = null;

            if (!wasInTransaction) {
                conn.setAutoCommit(true);
            }
        } catch (IOException e) {
            throw new CmsDbIoException(Messages.get().container(Messages.ERR_WRITING_TO_OUTPUT_STREAM_1, resourceId), e);
        } catch (SQLException e) {
            throw new CmsDbSqlException(org.opencms.db.generic.Messages.get().container(
                org.opencms.db.generic.Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            org.opencms.db.oracle.CmsSqlManager.closeAllInTransaction(
                m_sqlManager,
                dbc,
                conn,
                stmt,
                res,
                commit,
                wasInTransaction);
        }
    }

    /**
     * @see org.opencms.db.I_CmsVfsDriver#readTopLatestResourceTree(org.opencms.db.CmsDbContext, CmsUUID, java.lang.String, int, CmsResourceState, long, long, long, long, long, long, int, int)
     */
    public List<CmsResource> readTopLatestResourceTree(
        CmsDbContext dbc,
        CmsUUID projectId,
        String parentPath,
        int type,
        CmsResourceState state,
        long lastModifiedAfter,
        long lastModifiedBefore,
        long releasedAfter,
        long releasedBefore,
        long expiredAfter,
        long expiredBefore,
        int mode,
        int top) throws CmsDataAccessException {

        List<CmsResource> result = new ArrayList<CmsResource>();

        StringBuffer conditions = new StringBuffer();
        List params = new ArrayList(5);

        // prepare the selection criteria
        prepareProjectCondition(projectId, mode, conditions, params);
        prepareResourceCondition(projectId, mode, conditions);
        prepareTypeCondition(projectId, type, mode, conditions, params);
        prepareTimeRangeCondition(projectId, lastModifiedAfter, lastModifiedBefore, conditions, params);
        prepareReleasedTimeRangeCondition(projectId, releasedAfter, releasedBefore, conditions, params);
        prepareExpiredTimeRangeCondition(projectId, expiredAfter, expiredBefore, conditions, params);
        preparePathCondition(projectId, parentPath, mode, conditions, params);
        prepareStateCondition(projectId, state, mode, conditions, params);
        

        // now read matching resources within the subtree 
        ResultSet res = null;
        PreparedStatement stmt = null;
        Connection conn = null;
        StringBuffer queryBuf = new StringBuffer(256);

        try {
            conn = m_sqlManager.getConnection(dbc);
            queryBuf.append(m_sqlManager.readQuery(projectId, "C_ORACLE_RESOURCES_READ_TREE"));
            queryBuf.append(conditions);
            queryBuf.append(" ");
            queryBuf.append(m_sqlManager.readQuery(projectId, "C_ORACLE_RESOURCES_ORDER_BY_DATELASTMODIFIED"));
            stmt = m_sqlManager.getPreparedStatementForSql(conn, queryBuf.toString());

            int i = 0;
            for (; i < params.size(); i++) {
                Object obj = params.get(i);
                if (obj instanceof String) {
                    stmt.setString(i + 1, (String) obj);
                } else if (obj instanceof Integer) {
                	stmt.setInt(i + 1, (Integer) obj);
                }
            }
            stmt.setInt(i + 1, top);

            res = stmt.executeQuery();
            while (res.next()) {
                CmsResource resource = createPagedResource(res, projectId);
                result.add(resource);
            }

            m_sqlManager.closeAll(dbc, conn, stmt, res);
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
     * @see org.opencms.db.I_CmsVfsDriver#readPagedLatestResourceTree(org.opencms.db.CmsDbContext, CmsUUID, java.lang.String, int, CmsResourceState, long, long, long, long, long, long, int, int, int)
     */
    public List<CmsResource> readPagedLatestResourceTree(
        CmsDbContext dbc,
        CmsUUID projectId,
        String parentPath,
        int type,
        CmsResourceState state,
        long lastModifiedAfter,
        long lastModifiedBefore,
        long releasedAfter,
        long releasedBefore,
        long expiredAfter,
        long expiredBefore,
        int mode,
        int startRow,
        int rowsInPage) throws CmsDataAccessException {

        List<CmsResource> result = new ArrayList<CmsResource>();

        StringBuffer conditions = new StringBuffer();
        List params = new ArrayList(5);

        // prepare the selection criteria
        prepareProjectCondition(projectId, mode, conditions, params);
        prepareResourceCondition(projectId, mode, conditions);
        prepareTypeCondition(projectId, type, mode, conditions, params);
        prepareTimeRangeCondition(projectId, lastModifiedAfter, lastModifiedBefore, conditions, params);
        prepareReleasedTimeRangeCondition(projectId, releasedAfter, releasedBefore, conditions, params);
        prepareExpiredTimeRangeCondition(projectId, expiredAfter, expiredBefore, conditions, params);
        preparePathCondition(projectId, parentPath, mode, conditions, params);
        prepareStateCondition(projectId, state, mode, conditions, params);
        

        // now read matching resources within the subtree 
        ResultSet res = null;
        PreparedStatement stmt = null;
        Connection conn = null;

        try {
            conn = m_sqlManager.getConnection(dbc);
            StringBuffer queryBuf = new StringBuffer(256);
            queryBuf.append(m_sqlManager.readQuery(projectId, "C_ORACLE_RESOURCES_READ_TREE_PAGED"));
            queryBuf.append(conditions);
            queryBuf.append(" ");
            queryBuf.append(m_sqlManager.readQuery(projectId, "C_ORACLE_RESOURCES_PAGED_ORDER_BY_DATELASTMODIFIED"));
            stmt = m_sqlManager.getPreparedStatementForSql(conn, queryBuf.toString());
            
            int i = 0;
            for (; i < params.size(); i++) {
                Object obj = params.get(i);
                if (obj instanceof String) {
                    stmt.setString(i + 1, (String) obj);
                } else if (obj instanceof Integer) {
                	stmt.setInt(i + 1, (Integer) obj);
                }
            }
            stmt.setInt(i + 1, startRow);
            stmt.setInt(i + 2, startRow + rowsInPage);

            res = stmt.executeQuery();
            while (res.next()) {
                CmsResource resource = createPagedResource(res, projectId);
                result.add(resource);
            }

            m_sqlManager.closeAll(dbc, conn, stmt, res);
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
     * @see org.opencms.db.I_CmsVfsDriver#createResource(java.sql.ResultSet, CmsUUID)
     */
    protected CmsResource createPagedResource(ResultSet res, CmsUUID projectId) throws SQLException {

        CmsUUID structureId = new CmsUUID(res.getString(m_sqlManager.readQuery("C_RESOURCES_STRUCTURE_ID")));
        CmsUUID resourceId = new CmsUUID(res.getString(m_sqlManager.readQuery("C_RESOURCES_RESOURCE_ID")));
        String resourcePath = res.getString(m_sqlManager.readQuery("C_RESOURCES_RESOURCE_PATH"));
        int resourceType = res.getInt(m_sqlManager.readQuery("C_RESOURCES_RESOURCE_TYPE"));
        int resourceFlags = res.getInt(m_sqlManager.readQuery("C_RESOURCES_RESOURCE_FLAGS"));
        CmsUUID resourceProjectLastModified = new CmsUUID(
            res.getString(m_sqlManager.readQuery("C_ORACLE_RESOURCES_PROJECT_LASTMODIFIED")));
        int resourceState = res.getInt(m_sqlManager.readQuery("C_RESOURCES_STATE"));
        int structureState = res.getInt(m_sqlManager.readQuery("C_RESOURCES_STRUCTURE_STATE"));
        long dateCreated = res.getLong(m_sqlManager.readQuery("C_RESOURCES_DATE_CREATED"));
        long dateLastModified = res.getLong(m_sqlManager.readQuery("C_RESOURCES_DATE_LASTMODIFIED"));
        long dateReleased = res.getLong(m_sqlManager.readQuery("C_RESOURCES_DATE_RELEASED"));
        long dateExpired = res.getLong(m_sqlManager.readQuery("C_RESOURCES_DATE_EXPIRED"));
        int resourceSize = res.getInt(m_sqlManager.readQuery("C_RESOURCES_SIZE"));
        boolean isFolder = CmsFolder.isFolderSize(resourceSize);
        if (isFolder) {
            // in case of folder type ensure, that the root path has a trailing slash
            resourcePath = CmsFileUtil.addTrailingSeparator(resourcePath);
        }
        long dateContent = isFolder ? -1 : res.getLong(m_sqlManager.readQuery("C_RESOURCES_DATE_CONTENT"));
        CmsUUID userCreated = new CmsUUID(res.getString(m_sqlManager.readQuery("C_RESOURCES_USER_CREATED")));
        CmsUUID userLastModified = new CmsUUID(res.getString(m_sqlManager.readQuery("C_RESOURCES_USER_LASTMODIFIED")));
        int siblingCount = res.getInt(m_sqlManager.readQuery("C_RESOURCES_SIBLING_COUNT"));
        int resourceVersion = res.getInt(m_sqlManager.readQuery("C_RESOURCES_VERSION"));
        int structureVersion = res.getInt(m_sqlManager.readQuery("C_RESOURCES_STRUCTURE_VERSION"));

        int newState = (structureState > resourceState) ? structureState : resourceState;
        // if there is a change increase the version number
        int newVersion = resourceVersion + structureVersion + (newState > 0 ? 1 : 0);

        CmsResource newResource = new CmsResource(
            structureId,
            resourceId,
            resourcePath,
            resourceType,
            isFolder,
            resourceFlags,
            resourceProjectLastModified,
            CmsResourceState.valueOf(newState),
            dateCreated,
            userCreated,
            dateLastModified,
            userLastModified,
            dateReleased,
            dateExpired,
            siblingCount,
            resourceSize,
            dateContent,
            newVersion);

        return newResource;
    }
}
