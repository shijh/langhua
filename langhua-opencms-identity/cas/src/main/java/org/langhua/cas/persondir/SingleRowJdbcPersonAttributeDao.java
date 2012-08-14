/*
 * This library is part of OpenCms Identity module of Langhua Opensource Foundation
 *
 * Copyright (C) 2009  Langhua Opensource Foundation (http://langhua.org)
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
 * project website: http://langhua.org/opensource/opencms/opencms-identity/
 * 
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.cas.persondir;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.jasig.services.persondir.IPersonAttributes;
import org.jasig.services.persondir.support.CaseInsensitiveAttributeNamedPersonImpl;
import org.jasig.services.persondir.support.CaseInsensitiveNamedPersonImpl;
import org.jasig.services.persondir.support.MultivaluedPersonAttributeUtils;
import org.jasig.services.persondir.support.jdbc.AbstractJdbcPersonAttributeDao;
import org.jasig.services.persondir.support.jdbc.ColumnMapParameterizedRowMapper;
import org.springframework.jdbc.core.simple.ParameterizedRowMapper;

/**
 * An {@link org.jasig.services.persondir.IPersonAttributeDao}
 * implementation that maps from column names in the result of a SQL query
 * to attribute names. <br>
 * You must set a Map from column names to attribute names and only column names
 * appearing as keys in that map will be used.
 * 
 * <br>
 * <br>
 * Configuration:
 * <table border="1">
 *     <tr>
 *         <th align="left">Property</th>
 *         <th align="left">Description</th>
 *         <th align="left">Required</th>
 *         <th align="left">Default</th>
 *     </tr>
 *     <tr>
 *         <td align="right" valign="top">columnsToAttributes</td>
 *         <td>
 *             The {@link Map} of {@link String} columns names to {@link String} or {@link Set}s
 *             of {@link String}s to use as attribute names in the returned Map. If a column name
 *             is not in the map the column name will be used in as the returned attribute name.
 *         </td>
 *         <td valign="top">No</td>
 *         <td valign="top">{@link java.util.Collections#EMPTY_MAP}</td>
 *     </tr>
 * </table>
 * 
 * @author andrew.petro@yale.edu
 * @author Eric Dalquist
 * @version $Revision: 44949 $ $Date: 2009-02-17 03:13:14 +0800 (二, 2009-02-17) $
 * @since uPortal 2.5
 * 
 * This is copied from cas persondir. Some codes are changed.
 * @author Shi Yusen, shiys@langhua.cn
 */
public class SingleRowJdbcPersonAttributeDao extends AbstractJdbcPersonAttributeDao<Map<String, Object>> {
    private static final ParameterizedRowMapper<Map<String, Object>> MAPPER = new ColumnMapParameterizedRowMapper(true);

    /**
     * Creates a new MultiRowJdbcPersonAttributeDao specifying the DataSource and SQL to use.
     * 
     * @param ds The DataSource to get connections from for executing queries, may not be null.
     * @param attrList Sets the query attribute list
     * @param sql The SQL to execute for user attributes, may not be null.
     */
    public SingleRowJdbcPersonAttributeDao(DataSource ds, String sql) {
        super(ds, sql);
    }

    /* (non-Javadoc)
     * @see org.jasig.services.persondir.support.jdbc.AbstractJdbcPersonAttributeDao#getRowMapper()
     */
    @Override
    protected ParameterizedRowMapper<Map<String, Object>> getRowMapper() {
        return MAPPER;
    }

    
    /* (non-Javadoc)
     * @see org.jasig.services.persondir.support.jdbc.AbstractJdbcPersonAttributeDao#parseAttributeMapFromResults(java.util.List, java.lang.String)
     */
    @Override
    protected List<IPersonAttributes> parseAttributeMapFromResults(List<Map<String, Object>> queryResults, String queryUserName) {
        final List<IPersonAttributes> peopleAttributes = new ArrayList<IPersonAttributes>(queryResults.size());
        
        // merge the rows of results to one row, please note, here's an assume: the results are for the same user
        // modified by Shi Yusen, shiys@langhua.cn
        Map<String, List<Object>> firstQueryResult = null;
        for (final Map<String, Object> queryResult : queryResults) {
            final Map<String, List<Object>> multivaluedQueryResult = MultivaluedPersonAttributeUtils.toMultivaluedMap(queryResult);
            
            if (firstQueryResult == null) {
            	firstQueryResult = multivaluedQueryResult;
            } else {
            	for (String key : firstQueryResult.keySet()) {
            		List<Object> original = firstQueryResult.get(key);
        			List<Object> newList = new ArrayList<Object>();
        			newList.addAll(original);
            		for (Object value : multivaluedQueryResult.get(key)) {
                		if (!newList.contains(value)) {
                			newList.add(value);
                		}
            		}
        			firstQueryResult.put(key, newList);
            	}
            }
        }
        
        final IPersonAttributes person;
        if (queryUserName != null) {
            person = new CaseInsensitiveNamedPersonImpl(queryUserName, firstQueryResult);
        }
        else {
            //Create the IPersonAttributes doing a best-guess at a userName attribute
            final String userNameAttribute = this.getConfiguredUserNameAttribute();
            person = new CaseInsensitiveAttributeNamedPersonImpl(userNameAttribute, firstQueryResult);
        }
        peopleAttributes.add(person);
        return peopleAttributes;
    }
}