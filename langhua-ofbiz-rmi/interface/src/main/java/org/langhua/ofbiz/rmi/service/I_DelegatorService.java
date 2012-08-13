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
 * Interface of RMI Delegator Services which will be an RMI counterpart of org.ofbiz.entity.GenericDelegator.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public interface I_DelegatorService {
	
	public static final String RMI_RESULTS = "results";
    
    /** 
     * Creates an Entity in the form of a GenericValue without persisting it.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#makeValue(String)},
     * {@link org.ofbiz.entity.GenericDelegator#makeValue(String, Map)}, {@link org.ofbiz.entity.GenericDelegator#makeValue(String, Object[])},
     * {@link org.ofbiz.entity.GenericDelegator#makeValue(org.w3c.dom.Element)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.entityName String
     * @param context.fields Map
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: GenericValue if success
     */
    public Map<String, Object> makeValue(DispatchContext dctx, Map<String, ?> context);
    
    /**
     * Find a Generic Entity by its Primary Key.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#findOne(String, boolean, Object[])},
     * {@link org.ofbiz.entity.GenericDelegator#findOne(String, Map, boolean)}, {@link org.ofbiz.entity.GenericDelegator#findByPrimaryKey(org.ofbiz.entity.GenericPK)},
     * {@link org.ofbiz.entity.GenericDelegator#findByPrimaryKey(String, Map)}, {@link org.ofbiz.entity.GenericDelegator#findByPrimaryKey(String, Object[])},
     * {@link org.ofbiz.entity.GenericDelegator#findByPrimaryKeyCache(org.ofbiz.entity.GenericPK)}, {@link org.ofbiz.entity.GenericDelegator#findByPrimaryKeyCache(String, Map)},
     * {@link org.ofbiz.entity.GenericDelegator#findByPrimaryKeyCache(String, Object[])}, {@link org.ofbiz.entity.GenericDelegator#findByPrimaryKeyCacheSingle(String, Object)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.entityName String
     * @param context.primaryKey GenericPK
     * @param context.lookupFields Map
     * @param context.lookupObjects Object[]
     * @param context.singlePkValue Object
     * @param context.useCache Boolean
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: GenericValue if success
     */
    public Map<String, Object> findOne(DispatchContext dctx, Map<String, ?> context);

    /**
     * Finds GenericValues by the conditions specified in the EntityCondition object.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#findAll(String)}, {@link org.ofbiz.entity.GenericDelegator#findAll(String, java.util.List)},
     * {@link org.ofbiz.entity.GenericDelegator#findAll(String, String[])}, {@link org.ofbiz.entity.GenericDelegator#findAllCache(String)},
     * {@link org.ofbiz.entity.GenericDelegator#findAllCache(String, java.util.List)}, {@link org.ofbiz.entity.GenericDelegator#findAllCache(String, String[])},
     * {@link org.ofbiz.entity.GenericDelegator#findList(String, org.ofbiz.entity.condition.EntityCondition, java.util.Set, java.util.List, org.ofbiz.entity.util.EntityFindOptions, boolean)},
     * {@link org.ofbiz.entity.GenericDelegator#findByAnd(String, org.ofbiz.entity.condition.EntityCondition[])}, {@link org.ofbiz.entity.GenericDelegator#findByAnd(String, java.util.List)},
     * {@link org.ofbiz.entity.GenericDelegator#findByAnd(String, Map)}, {@link org.ofbiz.entity.GenericDelegator#findByAnd(String, Object[])},
     * {@link org.ofbiz.entity.GenericDelegator#findByAnd(String, java.util.List, java.util.List)}, {@link org.ofbiz.entity.GenericDelegator#findByAnd(String, Map, java.util.List)},
     * {@link org.ofbiz.entity.GenericDelegator#findByAndCache(String, Object[])}, {@link org.ofbiz.entity.GenericDelegator#findByAndCache(String, Map, java.util.List)},
     * {@link org.ofbiz.entity.GenericDelegator#findByCondition(String, org.ofbiz.entity.condition.EntityCondition, java.util.Collection, java.util.List)}, {@link org.ofbiz.entity.GenericDelegator#findByCondition(String, org.ofbiz.entity.condition.EntityCondition, org.ofbiz.entity.condition.EntityCondition, java.util.Collection, java.util.List, org.ofbiz.entity.util.EntityFindOptions)},
     * {@link org.ofbiz.entity.GenericDelegator#findByConditionCache(String, org.ofbiz.entity.condition.EntityCondition, java.util.Collection, java.util.List)}, {@link org.ofbiz.entity.GenericDelegator#findByLike(String, Map)},
     * {@link org.ofbiz.entity.GenericDelegator#findByLike(String, Object[])}, {@link org.ofbiz.entity.GenericDelegator#findByLike(String, Map, java.util.List)},
     * {@link org.ofbiz.entity.GenericDelegator#findByOr(String, org.ofbiz.entity.condition.EntityCondition[])}, {@link org.ofbiz.entity.GenericDelegator#findByOr(String, java.util.List)},
     * {@link org.ofbiz.entity.GenericDelegator#findByOr(String, Map)}, {@link org.ofbiz.entity.GenericDelegator#findByOr(String, Object[])},
     * {@link org.ofbiz.entity.GenericDelegator#findByOr(String, java.util.List, java.util.List)}, {@link org.ofbiz.entity.GenericDelegator#findByOr(String, Map, java.util.List)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.entityName String
     * @param context.expressions Map
     * @param context.orderBy List of String
     * @param context.fieldsToSelect Set
     * @param context.findOptions EntityFindOptions
     * @param context.useCache Boolean
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List of GenericValue if success
     */
    public Map<String, Object> findList(DispatchContext dctx, Map<String, ?> context);

    /**
     * Finds GenericValues by the conditions specified in the EntityCondition object.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#find(String, org.ofbiz.entity.condition.EntityCondition, org.ofbiz.entity.condition.EntityCondition, java.util.Set, List, org.ofbiz.entity.util.EntityFindOptions)}, {@link org.ofbiz.entity.GenericDelegator#findListIteratorByCondition(String, org.ofbiz.entity.condition.EntityCondition, java.util.Collection, List)},
     * {@link org.ofbiz.entity.GenericDelegator#findListIteratorByCondition(String, org.ofbiz.entity.condition.EntityCondition, org.ofbiz.entity.condition.EntityCondition, java.util.Collection, List, org.ofbiz.entity.util.EntityFindOptions)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.entityName String
     * @param context.whereEntityCondition EntityCondition
     * @param context.havingEntityCondition EntityCondition
     * @param context.fieldsToSelect Set
     * @param context.orderBy List of String
     * @param context.findOptions EntityFindOptions
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List of GenericValue
     */
    public Map<String, Object> find(DispatchContext dctx, Map<String, ?> context);

    /**
     * Get the next guaranteed unique seq id from the sequence with the given sequence name;
     * if the named sequence doesn't exist, it will be created.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#getNextSeqId(String)}, {@link org.ofbiz.entity.GenericDelegator#getNextSeqId(String, long)},
     * {@link org.ofbiz.entity.GenericDelegator#getNextSeqIdLong(String)}, {@link org.ofbiz.entity.GenericDelegator#getNextSeqIdLong(String, long)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.seqName String
     * @param context.staggerMax Long
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: String
     */
    public Map<String, Object> getNextSeqId(DispatchContext dctx, Map<String, ?> context);

    /**
     * Gets the instance of ModelEntity that corresponds to this delegator and the specified entityName.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#getModelEntity(String)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.entityName String
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: ModelEntity
     */
    public Map<String, Object> getModelEntity(DispatchContext dctx, Map<String, ?> context);

    /** 
     * Removes/deletes Generic Entity records found by the condition.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#removeByCondition(String, org.ofbiz.entity.condition.EntityCondition)}, {@link org.ofbiz.entity.GenericDelegator#removeByCondition(String, org.ofbiz.entity.condition.EntityCondition, boolean)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.entityName String
     * @param context.condition EntityCondition
     * @param context.doCacheClear Boolean
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: Integer
     */
    public Map<String, Object> removeByCondition(DispatchContext dctx, Map<String, ?> context);

    /**
     * Creates a Entity in the form of a GenericValue and write it to the datasource.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#create(org.ofbiz.entity.GenericPK)}, {@link org.ofbiz.entity.GenericDelegator#create(org.ofbiz.entity.GenericValue)},
     * {@link org.ofbiz.entity.GenericDelegator#create(org.ofbiz.entity.GenericPK, boolean)}, {@link org.ofbiz.entity.GenericDelegator#create(org.ofbiz.entity.GenericValue, boolean)},
     * {@link org.ofbiz.entity.GenericDelegator#create(String, Map)}, {@link org.ofbiz.entity.GenericDelegator#create(String, Object[])}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.genericValue GenericValue
     * @param context.doCacheClear Boolean
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: GenericValue
     */
    public Map<String, Object> create(DispatchContext dctx, Map<String, ?> context);

    /**
     * Store the Entity from the GenericValue to the persistent store.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#store(org.ofbiz.entity.GenericValue)}, {@link org.ofbiz.entity.GenericDelegator#store(org.ofbiz.entity.GenericValue, boolean)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.genericValue GenericValue
     * @param context.doCacheClear Boolean
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: Integer
     */
    public Map<String, Object> store(DispatchContext dctx, Map<String, ?> context);

    /**
     * Get the named Related Entity for the GenericValue from the persistent store.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#getRelated(String, org.ofbiz.entity.GenericValue)}, {@link org.ofbiz.entity.GenericDelegator#getRelated(String, Map, List, org.ofbiz.entity.GenericValue)},
     * {@link org.ofbiz.entity.GenericDelegator#getRelatedCache(String, org.ofbiz.entity.GenericValue)}, {@link org.ofbiz.entity.GenericDelegator#getRelatedByAnd(String, Map, org.ofbiz.entity.GenericValue)},
     * {@link org.ofbiz.entity.GenericDelegator#getRelatedOrderBy(String, List, org.ofbiz.entity.GenericValue)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.relationName String
     * @param context.genericValue GenericValue
     * @param context.byAndFields Map
     * @param context.orderBy List of String
     * @param context.useCache Boolean
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List of GenericValue
     */
    public Map<String, Object> getRelated(DispatchContext dctx, Map<String, ?> context);

    /**
     * Get related entity where relation is of type one, uses findByPrimaryKey.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#getRelatedOne(String, org.ofbiz.entity.GenericValue)}, {@link org.ofbiz.entity.GenericDelegator#getRelatedOneCache(String, org.ofbiz.entity.GenericValue)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.relationName String
     * @param context.genericValue GenericValue
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: GenericValue
     */
    public Map<String, Object> getRelatedOne(DispatchContext dctx, Map<String, ?> context);

    /**
     * Get find count of an entity name by the conditions provided.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#findCountByCondition(String, org.ofbiz.entity.condition.EntityCondition, org.ofbiz.entity.condition.EntityCondition, org.ofbiz.entity.util.EntityFindOptions)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.entityName String
     * @param context.whereEntityCondition EntityCondition
     * @param context.havingEntityCondition EntityCondition
     * @param context.findOptions EntityFindOptions
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: Long
     */
    public Map<String, Object> findCountByCondition(DispatchContext dctx, Map<String, ?> context);
    
    /**
     * Finds GenericValues by the conditions specified in the EntityCondition object.
     * This method covers {@link org.ofbiz.entity.GenericDelegator#find(String, org.ofbiz.entity.condition.EntityCondition, org.ofbiz.entity.condition.EntityCondition, java.util.Set, List, org.ofbiz.entity.util.EntityFindOptions)}, {@link org.ofbiz.entity.GenericDelegator#findListIteratorByCondition(String, org.ofbiz.entity.condition.EntityCondition, java.util.Collection, List)},
     * {@link org.ofbiz.entity.GenericDelegator#findListIteratorByCondition(String, org.ofbiz.entity.condition.EntityCondition, org.ofbiz.entity.condition.EntityCondition, java.util.Collection, List, org.ofbiz.entity.util.EntityFindOptions)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.dynamicViewEntity DynamicViewEntity
     * @param context.whereEntityCondition EntityCondition
     * @param context.havingEntityCondition EntityCondition
     * @param context.fieldsToSelect Collection
     * @param context.orderBy List of String
     * @param context.findOptions EntityFindOptions
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: EntityListIterator
     */
    public Map<String, Object> findListIteratorByCondition(DispatchContext dctx, Map<String, ?> context);
    
}
