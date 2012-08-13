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

import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.Set;

import org.ofbiz.base.util.Debug;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericPK;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.model.DynamicViewEntity;
import org.ofbiz.entity.model.ModelEntity;
import org.ofbiz.entity.util.EntityFindOptions;
import org.ofbiz.entity.util.EntityListIterator;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.ServiceUtil;

/**
 * Implements of RMI Delegator Services.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public class DelegatorService implements I_DelegatorService {
    
    public final String module = DelegatorService.class.getName();
    
    /**
     *  Empty constructor
     */
    public DelegatorService() {
    }
    
    /**
     *  Static method to get DelegatorService instance.
     *  This method name is defined in java.lang.Class.
     */
    public static DelegatorService newInstance() {
    	return new DelegatorService();
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#makeValue(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> makeValue(DispatchContext dctx, Map<String, ?> context) {
    	
        GenericDelegator delegator = dctx.getDelegator();
        String entityName = (String) context.get("entityName");
        Map<String, Object> fields = (Map<String, Object>) context.get("fields");
        
        GenericValue makeValue = delegator.makeValue(entityName, fields);

        Map<String, Object> result = ServiceUtil.returnSuccess();
        if (makeValue != null) {
        	result.put(I_DelegatorService.RMI_RESULTS, makeValue);
        }
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#findCountByCondition(DispatchContext, Map)
     */
    public Map<String, Object> findCountByCondition(DispatchContext dctx, Map<String, ?> context) {
    	
        GenericDelegator delegator = dctx.getDelegator();
        String entityName = (String) context.get("entityName");
        EntityCondition whereEntityCondition = (EntityCondition) context.get("whereEntityCondition");
        EntityCondition havingEntityCondition = (EntityCondition) context.get("havingEntityCondition");
        EntityFindOptions findOptions = (EntityFindOptions) context.get("findOptions");
        
        long value;
		try {
			value = delegator.findCountByCondition(entityName, whereEntityCondition, havingEntityCondition, findOptions);
		} catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, new Long(value));
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#findListIteratorByCondition(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> findListIteratorByCondition(DispatchContext dctx, Map<String, ?> context) {
    	
        GenericDelegator delegator = dctx.getDelegator();
        DynamicViewEntity dynamicViewEntity = (DynamicViewEntity) context.get("dynamicViewEntity");
        EntityCondition whereEntityCondition = (EntityCondition) context.get("whereEntityCondition");
        EntityCondition havingEntityCondition = (EntityCondition) context.get("havingEntityCondition");
        Collection<String> fieldsToSelect = (Collection<String>) context.get("fieldsToSelect");
        List<String> orderBy = (List<String>) context.get("orderBy");
        EntityFindOptions findOptions = (EntityFindOptions) context.get("findOptions");
        
        EntityListIterator value;
		try {
			value = delegator.findListIteratorByCondition(dynamicViewEntity, whereEntityCondition, havingEntityCondition, fieldsToSelect, orderBy, findOptions);
		} catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
			value = null;
		}

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#getNextSeqId(DispatchContext, Map)
     */
    public Map<String, Object> getNextSeqId(DispatchContext dctx, Map<String, ?> context) {
    	
        GenericDelegator delegator = dctx.getDelegator();
        String seqName = (String) context.get("seqName");
        Long staggerMax = (Long) context.get("staggerMax");
        
        String value = delegator.getNextSeqId(seqName, staggerMax.longValue());

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#getModelEntity(DispatchContext, Map)
     */
    public Map<String, Object> getModelEntity(DispatchContext dctx, Map<String, ?> context) {
    	
        GenericDelegator delegator = dctx.getDelegator();
        String entityName = (String) context.get("entityName");
        
        ModelEntity value = delegator.getModelEntity(entityName);
        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#removeByCondition(DispatchContext, Map)
     */
    public Map<String, Object> removeByCondition(DispatchContext dctx, Map<String, ?> context) {
    	
        GenericDelegator delegator = dctx.getDelegator();
        String entityName = (String) context.get("entityName");
        EntityCondition condition = (EntityCondition) context.get("condition");
        Boolean doCacheClear = (Boolean) context.get("doCacheClear");
        
        int value;
		try {
			value = delegator.removeByCondition(entityName, condition, doCacheClear.booleanValue());
		} catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, new Integer(value));
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#create(DispatchContext, Map)
     */
    public Map<String, Object> create(DispatchContext dctx, Map<String, ?> context) {
    	
        GenericDelegator delegator = dctx.getDelegator();
        GenericValue genericValue = (GenericValue) context.get("genericValue");
        Boolean doCacheClear = (Boolean) context.get("doCacheClear");
        
        GenericValue value;
		try {
			value = delegator.create(genericValue, doCacheClear.booleanValue());
		} catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#store(DispatchContext, Map)
     */
    public Map<String, Object> store(DispatchContext dctx, Map<String, ?> context) {
    	
        GenericDelegator delegator = dctx.getDelegator();
        GenericValue genericValue = (GenericValue) context.get("genericValue");
        Boolean doCacheClear = (Boolean) context.get("doCacheClear");
        
        int value;
		try {
			value = delegator.store(genericValue, doCacheClear.booleanValue());
		} catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, new Integer(value));
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#getRelatedOne(DispatchContext, Map)
     */
    public Map<String, Object> getRelatedOne(DispatchContext dctx, Map<String, ?> context) {
    	
        GenericDelegator delegator = dctx.getDelegator();
        String relationName = (String) context.get("relationName");
        GenericValue genericValue = (GenericValue) context.get("genericValue");
        
        GenericValue value;
		try {
			value = delegator.getRelatedOne(relationName, genericValue);
		} catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#getRelated(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> getRelated(DispatchContext dctx, Map<String, ?> context) {
    	
        GenericDelegator delegator = dctx.getDelegator();
        String relationName = (String) context.get("relationName");
        GenericValue genericValue = (GenericValue) context.get("genericValue");
        Map<String, ? extends Object> byAndFields = (Map<String, ? extends Object>) context.get("byAndFields");
        List<String> orderBy = (List<String>) context.get("orderBy");
        boolean useCache = ((Boolean) context.get("useCache")).booleanValue();
        
        List<GenericValue> value;
		try {
			if (useCache) {
				value = delegator.getRelatedCache(relationName, genericValue);
			} else {
				value = delegator.getRelated(relationName, byAndFields, orderBy, genericValue);
			}
		} catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#findOne(DispatchContext, Map)
     */
	@SuppressWarnings("unchecked")
	public Map<String, Object> findOne(DispatchContext dctx, Map<String, ?> context) {
        GenericDelegator delegator = dctx.getDelegator();
        String entityName = (String) context.get("entityName");
        GenericPK primaryKey = (GenericPK) context.get("primaryKey");
        Map<String, ? extends Object> lookupFields = (Map<String, ? extends Object>) context.get("lookupFields");
        Object singlePkValue = context.get("singlePkValue");
        boolean useCache = ((Boolean) context.get("useCache")).booleanValue();
        
        GenericValue findValue = null;
		try {
			if (entityName != null) {
				if (lookupFields != null) {
					findValue = delegator.findOne(entityName, lookupFields, useCache);
				} else if (singlePkValue != null) {
					findValue = delegator.findOne(entityName, delegator.makePKSingle(entityName, singlePkValue), useCache);
				}
			} else if (primaryKey != null) {
				findValue = delegator.findOne(primaryKey.getEntityName(), primaryKey, useCache);
			}
		} catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

        Map<String, Object> result = ServiceUtil.returnSuccess();
        if (findValue != null) {
        	result.put(I_DelegatorService.RMI_RESULTS, findValue);
        }
        return result;
	}

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#findList(DispatchContext, Map)
     */
	@SuppressWarnings("unchecked")
	public Map<String, Object> findList(DispatchContext dctx, Map<String, ?> context) {
        GenericDelegator delegator = dctx.getDelegator();
        String entityName = (String) context.get("entityName");
        Map<String, ? extends Object> expressions = (Map<String, ? extends Object>) context.get("expressions");
        List<String> orderBy = (List<String>) context.get("orderBy");
        Set<String> fieldsToSelect = (Set<String>) context.get("fieldsToSelect");
        EntityFindOptions findOptions = (EntityFindOptions) context.get("findOptions");
        boolean useCache = ((Boolean) context.get("useCache")).booleanValue();
        
        List<GenericValue> values;
		try {
			values = delegator.findList(entityName, EntityCondition.makeCondition(expressions), fieldsToSelect, orderBy, findOptions, useCache);
		} catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, values);
        return result;
	}

    /**
     * @see org.langhua.ofbiz.rmi.service.I_DelegatorService#find(DispatchContext, Map)
     */
	@SuppressWarnings("unchecked")
	public Map<String, Object> find(DispatchContext dctx, Map<String, ?> context) {
        GenericDelegator delegator = dctx.getDelegator();
        String entityName = (String) context.get("entityName");
        EntityCondition whereEntityCondition = (EntityCondition) context.get("whereEntityCondition");
        EntityCondition havingEntityCondition = (EntityCondition) context.get("havingEntityCondition");
        Set<String> fieldsToSelect = (Set<String>) context.get("fieldsToSelect");
        List<String> orderBy = (List<String>) context.get("orderBy");
        EntityFindOptions findOptions = (EntityFindOptions) context.get("findOptions");
        
        EntityListIterator values = null;
		try {
			values = delegator.find(entityName, whereEntityCondition, havingEntityCondition, fieldsToSelect, orderBy, findOptions);
		} catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
            return ServiceUtil.returnError(e.getMessage());
		}

        Map<String, Object> result = ServiceUtil.returnSuccess();
		if (values != null) {
	    	try {
				result.put(I_DelegatorService.RMI_RESULTS, values.getCompleteList());
			} catch (GenericEntityException e) {
	            Debug.logWarning(e.getMessage(), module);
	            return ServiceUtil.returnError(e.getMessage());
			}
		}
        return result;
	}
}
