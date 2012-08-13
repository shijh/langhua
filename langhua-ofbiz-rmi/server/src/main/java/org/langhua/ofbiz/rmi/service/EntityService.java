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

import org.ofbiz.entity.GenericEntity;
import org.ofbiz.entity.GenericPK;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.model.ModelEntity;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.ServiceUtil;

/**
 * Implements of RMI Entity Services.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public class EntityService implements I_EntityService {
    
    public final String module = EntityService.class.getName();

    /**
     *  Empty constructor
     */
    public EntityService() {
    }
    
    /**
     *  Static method to get EntityService instance
     *  This method name is defined in java.lang.Class.
     */
    public static EntityService newInstance() {
    	return new EntityService();
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityService#getPrimaryKey(DispatchContext, Map)
     */
    public Map<String, Object> getPrimaryKey(DispatchContext dctx, Map<String, ?> context) {
    	
        GenericEntity genericValue = (GenericEntity) context.get("genericEntity");
        
        GenericPK value = genericValue.getPrimaryKey();
        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityService#setFieldValue(DispatchContext, Map)
     */
    public Map<String, Object> setFieldValue(DispatchContext dctx, Map<String, ?> context) {
    	
        String name = (String) context.get("name");
        Object object = context.get("object");
        GenericValue genericValue = (GenericValue) context.get("genericValue");
        
		genericValue.set(name, object);

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, genericValue);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityService#create(DispatchContext, Map)
     */
    public Map<String, Object> createValue(DispatchContext dctx, Map<String, ?> context) {
    	
        Object genericValue = context.get("genericValue");
    	Object modelEntity = context.get("modelEntity");

    	GenericValue value = null;
    	if (genericValue != null) {
            value = GenericValue.create((GenericValue) genericValue);
    	} else if (modelEntity != null) {
    		value = GenericValue.create((ModelEntity) modelEntity);
    	}

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, value);
        return result;
    }

    /**
     * @see org.langhua.ofbiz.rmi.service.I_EntityService#setAllFields(DispatchContext, Map)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> setAllFields(DispatchContext dctx, Map<String, ?> context) {
    	
        Map<? extends Object, ? extends Object> fields = (Map<? extends Object, ? extends Object>) context.get("fields");
        Boolean setIfEmpty = (Boolean) context.get("setIfEmpty");
        String namePrefix = (String) context.get("namePrefix");
        Boolean pks = (Boolean) context.get("pks");
        GenericValue genericValue = (GenericValue) context.get("genericValue");
        
		genericValue.setAllFields(fields, setIfEmpty.booleanValue(), namePrefix, pks);

        Map<String, Object> result = ServiceUtil.returnSuccess();
    	result.put(I_DelegatorService.RMI_RESULTS, genericValue);
        return result;
    }

}
