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
 * Interface of RMI Entity Services which will be an counterpart of
 * org.ofbiz.entity.GenericEntity, org.ofbiz.entity.GenericValue and
 * org.ofbiz.entity.GenericPK.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public interface I_EntityService {

	/**
	 * Get a primary key of an entity. This method covers
	 * {@link org.ofbiz.entity.GenericEntity#getPrimaryKey()}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.genericEntity GenericEntity
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: GenericPK
	 */
	public Map<String, Object> getPrimaryKey(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Sets the named field to the passed value, even if the value is null. This
	 * method covers {@link org.ofbiz.entity.GenericEntity#set(String, Object)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.name String
     * @param context.object Object
     * @param context.genericValue GenericValue
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: GenericValue
	 */
	public Map<String, Object> setFieldValue(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Creates new GenericValue from existing GenericValue or ModelEntity. This method covers
	 * {@link org.ofbiz.entity.GenericValue#create(org.ofbiz.entity.GenericValue)},
	 * {@link org.ofbiz.entity.GenericValue#create(org.ofbiz.entity.model.ModelEntity)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.genericValue GenericValue
     * @param context.modelEntity ModelEntity
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: GenericValue
	 */
	public Map<String, Object> createValue(DispatchContext dctx, Map<String, ?> context);

	/**
	 * Intelligently sets fields on the entity from the Map of fields passed in.
	 * This method covers
	 * {@link org.ofbiz.entity.GenericValue#setAllFields(Map, boolean, String, Boolean)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.fields Map
     * @param context.setIfEmpty Boolean
     * @param context.namePrefix String
     * @param context.pks Boolean
     * @param context.genericValue GenericValue
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: GenericValue
	 */
	public Map<String, Object> setAllFields(DispatchContext dctx, Map<String, ?> context);

}
