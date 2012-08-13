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

import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.ofbiz.entity.GenericEntity;
import org.ofbiz.entity.GenericPK;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.ServiceUtil;

/**
 * Implements of RMI Entity Utility Services.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public class EntityUtilService implements I_EntityUtilService {
    
    public final String module = EntityUtilService.class.getName();

    /**
     *  Empty constructor
     */
    public EntityUtilService() {
    }
    
    /**
     *  Static method to get EntityUtilService instance
     *  This method name is defined in java.lang.Class.
     */
    public static EntityUtilService newInstance() {
    	return new EntityUtilService();
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#getIndexOf(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> getIndexOf(DispatchContext dctx, Map<String, ?> context) {
    	
        List<GenericValue> list = (List<GenericValue>) context.get("list");
        GenericValue genericValue = (GenericValue) context.get("genericValue");
        
		int value = list.indexOf(genericValue);

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, new Integer(value));
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#putIntoMap(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> putIntoMap(DispatchContext dctx, Map<String, ?> context) {
    	
        Map<GenericPK, Object> storeMap = (Map<GenericPK, Object>) context.get("storeMap");
        Object object = context.get("object");
        GenericPK genericPK = (GenericPK) context.get("genericPK");
        
		storeMap.put(genericPK, object);

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, storeMap);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#getLinkedList(DispatchContext, Map)
     */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getLinkedList(DispatchContext dctx, Map<String, ?> context) {
    	
    	List<Object> list = (List<Object>) context.get("list");
        
        List<Object> value = new LinkedList<Object>(list);

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#removeValueFromList(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> removeValueFromList(DispatchContext dctx, Map<String, ?> context) {
    	
    	List<Object> list = (List<Object>) context.get("list");
        GenericValue genericValue = (GenericValue) context.get("genericValue");
        
        list.remove(genericValue);

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, list);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#filterByDate(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> filterByDate(DispatchContext dctx, Map<String, ?> context) {
    	
    	List<? extends GenericEntity> datedValues = (List<? extends GenericEntity>) context.get("datedValues");
    	Timestamp moment = (Timestamp) context.get("moment");
    	String fromDateName = (String) context.get("fromDateName");
    	String thruDateName = (String) context.get("thruDateName");
    	Boolean allAreSame = (Boolean) context.get("allAreSame");
        
    	List<? extends GenericEntity> value = EntityUtil.filterByDate(datedValues, moment, fromDateName, thruDateName, allAreSame.booleanValue());

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#filterByAnd(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> filterByAnd(DispatchContext dctx, Map<String, ?> context) {
    	
    	List<? extends GenericEntity> list = (List<? extends GenericEntity>) context.get("list");
    	Map<String, ? extends Object> fields = (Map<String, ? extends Object>) context.get("fields");
    	List<? extends EntityCondition> exprs = (List<? extends EntityCondition>) context.get("exprs");
        
    	List<? extends GenericEntity> value = null;
    	if (exprs != null) {
    		value = EntityUtil.filterByAnd(list, exprs);
    	} else if (fields != null) {
    		value = EntityUtil.filterByAnd(list, fields);
    	}

        Map<String, Object> result = ServiceUtil.returnSuccess();
        if (value == null) {
        	result.put(I_DelegatorService.RMI_RESULTS, list);
        } else {
        	result.put(I_DelegatorService.RMI_RESULTS, value);
        }
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#orderBy(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> orderBy(DispatchContext dctx, Map<String, ?> context) {
    	
    	List<? extends GenericEntity> list = (List<? extends GenericEntity>) context.get("list");
    	List<String> orderByList = (List<String>) context.get("orderByList");
        
    	List<? extends GenericEntity> value = EntityUtil.orderBy(list, orderByList);

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#getFirst(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> getFirst(DispatchContext dctx, Map<String, ?> context) {
    	
    	List<GenericValue> list = (List<GenericValue>) context.get("list");
        
    	GenericValue value = EntityUtil.getFirst(list);

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#getFilterByDateExpr(DispatchContext, Map)
     */
    public Map<String, Object> getFilterByDateExpr(DispatchContext dctx, Map<String, ?> context) {
    	
    	Timestamp moment = (Timestamp) context.get("moment");
        
    	EntityCondition value = EntityUtil.getFilterByDateExpr(moment);

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#filterByOr(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> filterByOr(DispatchContext dctx, Map<String, ?> context) {
    	
    	List<? extends GenericEntity> values = (List<? extends GenericEntity>) context.get("values");
    	List<? extends EntityCondition> exprs = (List<? extends EntityCondition>) context.get("exprs");
        
    	List<? extends GenericEntity> value = EntityUtil.filterByOr(values, exprs);

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#filterByCondition(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> filterByCondition(DispatchContext dctx, Map<String, ?> context) {
    	
    	List<? extends GenericEntity> orderItems = (List<? extends GenericEntity>) context.get("orderItems");
    	EntityCondition entityCondition = (EntityCondition) context.get("entityCondition");
        
    	List<? extends GenericEntity> value = EntityUtil.filterByCondition(orderItems, entityCondition);

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#getFieldListFromEntityList(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> getFieldListFromEntityList(DispatchContext dctx, Map<String, ?> context) {
    	
    	List<GenericValue> genericValueList = (List<GenericValue>) context.get("genericValueList");
    	String fieldName = (String) context.get("fieldName");
    	Boolean distinct = (Boolean) context.get("distinct");
        
    	List<Object> value = EntityUtil.getFieldListFromEntityList(genericValueList, fieldName, distinct.booleanValue());

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityUtilService#getValueFromMap(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> getValueFromMap(DispatchContext dctx, Map<String, ?> context) {
    	
        GenericPK genericPK = (GenericPK) context.get("genericPK");
        Map<GenericPK, Object> lookupMap = (Map<GenericPK, Object>) context.get("lookupMap");
        
        Object value = lookupMap.get(genericPK);
        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

}
