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
package org.langhua.ofbiz.rmi.client;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.langhua.ofbiz.rmi.service.I_CartServices;
import org.langhua.ofbiz.rmi.service.I_DelegatorService;
import org.langhua.ofbiz.rmi.service.I_EntityService;
import org.langhua.ofbiz.rmi.service.I_EntityUtilService;
import org.langhua.ofbiz.rmi.service.I_LoginServices;
import org.langhua.ofbiz.rmi.service.I_OrderServices;
import org.langhua.ofbiz.rmi.service.I_ProductServices;
import org.ofbiz.base.util.Debug;

/**
 * Interface of RMI Client
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public interface I_RMIClient {
	
    public static String module = I_RMIClient.class.getName();

    public static Set<String> RMI_SERVICES = RMIServiceMethods.getMethodNames();
	
	public class RMIServiceMethods {
		public static Set<String> getMethodNames() {
			List<Method> DELEGATOR_METHODS = Arrays.asList(I_DelegatorService.class.getMethods());
			List<Method> ENTITY_METHODS = Arrays.asList(I_EntityService.class.getMethods());
			List<Method> ENTITYUTIL_METHODS = Arrays.asList(I_EntityUtilService.class.getMethods());
			List<Method> LOGIN_METHODS = Arrays.asList(I_LoginServices.class.getMethods());
			List<Method> ORDER_METHODS = Arrays.asList(I_OrderServices.class.getMethods());
			List<Method> PRODUCT_METHODS = Arrays.asList(I_ProductServices.class.getMethods());
			List<Method> CART_METHODS = Arrays.asList(I_CartServices.class.getMethods());

			Set<String> names = new HashSet<String>();
			// add the RMI test method
			names.add("testScv");
			for (Method method : DELEGATOR_METHODS) {
				if (names.contains(method.getName())) {
					Debug.logError("RMI Service name " + method.getName() + " in " + method.getClass().getName() + " is DUPLICATED, please correct this ERROR!" , module);
				} else {
					names.add(method.getName());
				}
			}
			
			for (Method method : ENTITY_METHODS) {
				if (names.contains(method.getName())) {
					Debug.logError("RMI Service name " + method.getName() + " in " + method.getClass().getName() + " is DUPLICATED, please correct this ERROR!" , module);
				} else {
					names.add(method.getName());
				}
			}

			for (Method method : ENTITYUTIL_METHODS) {
				if (names.contains(method.getName())) {
					Debug.logError("RMI Service name " + method.getName() + " in " + method.getClass().getName() + " is DUPLICATED, please correct this ERROR!" , module);
				} else {
					names.add(method.getName());
				}
			}
			
			for (Method method : LOGIN_METHODS) {
				if (names.contains(method.getName())) {
					Debug.logError("RMI Service name " + method.getName() + " in " + method.getClass().getName() + " is DUPLICATED, please correct this ERROR!" , module);
				} else {
					names.add(method.getName());
				}
			}
			
			for (Method method : ORDER_METHODS) {
				if (names.contains(method.getName())) {
					Debug.logError("RMI Service name " + method.getName() + " in " + method.getClass().getName() + " is DUPLICATED, please correct this ERROR!" , module);
				} else {
					names.add(method.getName());
				}
			}
			
			for (Method method : PRODUCT_METHODS) {
				if (names.contains(method.getName())) {
					Debug.logError("RMI Service name " + method.getName() + " in " + method.getClass().getName() + " is DUPLICATED, please correct this ERROR!" , module);
				} else {
					names.add(method.getName());
				}
			}
			
			for (Method method : CART_METHODS) {
				if (names.contains(method.getName())) {
					Debug.logError("RMI Service name " + method.getName() + " in " + method.getClass().getName() + " is DUPLICATED, please correct this ERROR!" , module);
				} else {
					names.add(method.getName());
				}
			}
			
			return names;
		}
	}
	
	/**
	 * Indicate service to call
	 * {@link org.ofbiz.service.rmi.RemoteDispatcher#runAsync(String, Map)}
	 */
	public static final int RUN_ASYNC = 1;
	
	/**
	 * Indicate service to call
	 * {@link org.ofbiz.service.rmi.RemoteDispatcher#runAsync(String, Map, boolean)}
	 * The params Map in {@link #service(String, Map, Map)} and {@link #service(String, Map, int, Map)} should contain:
	 * params.persist Boolean
	 */
	public static final int RUN_ASYNC_PERSIST = 2;
	
	/**
	 * Indicate service to call
	 * {@link org.ofbiz.service.rmi.RemoteDispatcher#runAsync(String, Map, org.ofbiz.service.GenericRequester)}
	 * The params Map in {@link #service(String, Map, Map)} and {@link #service(String, Map, int, Map)} should contain:
	 * params.requester GenericRequester
	 */
	public static final int RUN_ASYNC_REQUESTER = 4;
	
	/**
	 * Indicate service to call
	 * {@link org.ofbiz.service.rmi.RemoteDispatcher#runAsync(String, Map, org.ofbiz.service.GenericRequester, boolean)}
	 * The params Map in {@link #service(String, Map, Map)} and {@link #service(String, Map, int, Map)} should contain:
	 * params.requester GenericRequester
	 * params.persist Boolean
	 */
	public static final int RUN_ASYNC_REQUESTER_PERSIST = 8;
	
	/**
	 * Indicate service to call
	 * {@link org.ofbiz.service.rmi.RemoteDispatcher#runAsyncWait(String, Map)}
	 */
	public static final int RUN_ASYNCWAIT = 16;
	
	/**
	 * Indicate service to call
	 * {@link org.ofbiz.service.rmi.RemoteDispatcher#runAsyncWait(String, Map, boolean)}
	 * The params Map in {@link #service(String, Map, Map)} and {@link #service(String, Map, int, Map)} should contain:
	 * params.persist Boolean
	 */
	public static final int RUN_ASYNCWAIT_PERSIST = 32;
	
	/**
	 * Indicate service to call
	 * {@link org.ofbiz.service.rmi.RemoteDispatcher#runSync(String, Map)}
	 */
	public static final int RUN_SYNC = 64;
	
	/**
	 * Indicate service to call
	 * {@link org.ofbiz.service.rmi.RemoteDispatcher#runSync(String, Map, int, boolean)}
	 * The params Map in {@link #service(String, Map, Map)} and {@link #service(String, Map, int, Map)} should contain:
	 * params.transactionTimeout Integer
	 * params.requireNewTransaction Boolean
	 */
	public static final int RUN_SYNC_TRANSACTION = 128;
	
	/**
	 * Indicate service to call
	 * {@link org.ofbiz.service.rmi.RemoteDispatcher#runSyncIgnore(String, Map)}
	 */
	public static final int RUN_SYNCIGNORE = 256;
	
	/**
	 * Indicate service to call
	 * {@link org.ofbiz.service.rmi.RemoteDispatcher#runSyncIgnore(String, Map, int, boolean)}
	 * The params Map in {@link #service(String, Map, Map)} and {@link #service(String, Map, int, Map)} should contain:
	 * params.transactionTimeout Integer
	 * params.requireNewTransaction Boolean
	 */
	public static final int RUN_SYNCIGNORE_TRANSACTION = 512;
	
	/**
	 * Indicate service to call
	 * {@link org.ofbiz.service.rmi.RemoteDispatcher#schedule(String, Map, long)}
	 * The params Map in {@link #service(String, Map, Map)} and {@link #service(String, Map, int, Map)} should contain:
	 * params.startTime Long
	 */
	public static final int SCHEDULE = 1024;
	
	/**
	 * Indicate service to call
	 * {@link org.ofbiz.service.rmi.RemoteDispatcher#schedule(String, Map, long, int, int, long)}
	 * The params Map in {@link #service(String, Map, Map)} and {@link #service(String, Map, int, Map)} should contain:
	 * params.startTime Long
	 * params.frequency Integer
	 * params.interval Integer
	 * params.endTime Long
	 */
	public static final int SCHEDULE_START_END = 2048;
	
	/**
	 * Check whether the parameters in context map are correct for the rmi service specified.
	 * @param serviceName String of service name
	 * @param context Map of parameters
	 * @return true if the parameters are correct; otherwise false. Default is true.
	 */
	public boolean checkContext(String serviceName, Map<String, ? extends Object> context);

	/**
	 * Run an RMI service by runSync method.
	 * @param serviceName String of service name
	 * @param context Map of parameters
	 * @return Map of RMI service result
	 */
	public Map<String, ? extends Object> service(String serviceName, Map<String, ? extends Object> context);

	/**
	 * Run an RMI service by runSync method.
	 * @param serviceName String of service name
	 * @param context Map of service parameters
	 * @param params Map of command parameters
	 * @return Map of RMI service result
	 */
	public Map<String, ? extends Object> service(String serviceName, Map<String, ? extends Object> context, Map<String, ? extends Object> params);

	/**
	 * Run an RMI service.
	 * @param serviceName String of service name
	 * @param context Map of service parameters
	 * @param command int to indicate which RMI command to run
	 * @param params Map of command parameters
	 * @return Map of RMI service result
	 */
	public Map<String, ? extends Object> service(String serviceName, Map<String, ? extends Object> context, int command, Map<String, ? extends Object> params);
	
}
