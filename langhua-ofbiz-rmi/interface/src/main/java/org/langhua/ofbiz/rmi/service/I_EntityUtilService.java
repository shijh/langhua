/*
 * This library is part of OFBiz-RMI Component of Langhua
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
 * For the latest version about this component, please see the
 * project website: http://langhua.org/opensource/ofbiz/rmi/
 * 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.ofbiz.rmi.service;

import java.util.Map;

import org.ofbiz.service.DispatchContext;

/**
 * Interface of RMI Entity Utility Services which is a counterpart of
 * org.ofbiz.entity.util.EntityUtil and some other Map/List operations.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public interface I_EntityUtilService {

	/**
	 * Clone a List of GenericEntity to a new one. This operation has to be done
	 * in server side.
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.list List
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List
	 */
	public Map<String, Object> getLinkedList(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Remove a GenericValue from a List of GenericValue. This operation has to be
	 * done in server side.
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.list List
     * @param context.genericValue GenericValue
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List of GenericValue
	 */
	public Map<String, Object> removeValueFromList(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Get the index of a GenericValue in a List. This operation has to be done
	 * in server side.
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.list List
     * @param context.genericValue GenericValue
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: Integer
	 */
	public Map<String, Object> getIndexOf(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Put an Object into Map<GenericPK, Object>. This operation has to be done
	 * in server side.
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.storeMap Map
     * @param context.object Object
     * @param context.genericPK GenericPK
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: Map<GenericPK, Object>
	 */
	public Map<String, Object> putIntoMap(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Get the values that are currently active. This operation has to be done
	 * in server side and use
	 * {@link org.ofbiz.entity.util.EntityUtil#filterByDate(java.util.List, java.sql.Timestamp, String, String, boolean)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.datedValues List
     * @param context.moment Timestamp
     * @param context.fromDateName String
     * @param context.thruDateName String
     * @param context.allAreSame Boolean
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List
	 */
	public Map<String, Object> filterByDate(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Get the values that match all of the exprs in list. This method covers
	 * {@link org.ofbiz.entity.util.EntityUtil#filterByAnd(java.util.List, java.util.List)},
	 * {@link org.ofbiz.entity.util.EntityUtil#filterByAnd(java.util.List, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.list List
     * @param context.fields Map
     * @param context.exprs List
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List
	 */
	public Map<String, Object> filterByAnd(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Get the values in the order specified. This method covers
	 * {@link org.ofbiz.entity.util.EntityUtil#orderBy(java.util.Collection, java.util.List)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.list List
     * @param context.orderByList List of String
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List
	 */
	public Map<String, Object> orderBy(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Get the first GenericValue from a List. This method covers
	 * {@link org.ofbiz.entity.util.EntityUtil#getFirst(java.util.List)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.list List
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: GenericValue
	 */
	public Map<String, Object> getFirst(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Get an EntityCondition of "fromDate" and "thruDate" according to the
	 * Timestamp provided. This method covers
	 * {@link org.ofbiz.entity.util.EntityUtil#getFilterByDateExpr(java.sql.Timestamp)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.moment Timestamp
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: EntityCondition
	 */
	public Map<String, Object> getFilterByDateExpr(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Get a List of GenericValue that match any of the exprs in list. This method covers
	 * {@link org.ofbiz.entity.util.EntityUtil#filterByOr(java.util.List, java.util.List)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.values List
     * @param context.exprs EntityCondition
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List
	 */
	public Map<String, Object> filterByOr(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Get a new List of GenericValue that match the specified EntityCondition. This method covers
	 * {@link org.ofbiz.entity.util.EntityUtil#filterByCondition(java.util.List, org.ofbiz.entity.condition.EntityCondition)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderItems List
     * @param context.entityCondition EntityCondition
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List
	 */
	public Map<String, Object> filterByCondition(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Get a new field List from a GenericValue List. This method covers
	 * {@link org.ofbiz.entity.util.EntityUtil#getFieldListFromEntityList(java.util.List, String, boolean)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.genericValueList List
     * @param context.fieldName String
     * @param context.distinct Boolean
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List
	 */
	public Map<String, Object> getFieldListFromEntityList(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Get a value from Map by GenericPK. This operation has to be done in server side.
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.genericPK GenericPK
     * @param context.lookupMap Map
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: Object
	 */
	public Map<String, Object> getValueFromMap(DispatchContext dctx, Map<String, ?> context);
	
}
