/*
 * This library is part of OFBiz-jBPM Component of Langhua
 *
 * Copyright (C) 2010  Langhua Opensource Foundation (http://langhua.org)
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
 * project website: http://langhua.org/opensource/ofbiz/ofbiz-jbpm/
 * 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 * 
 * For more information on JBPM, please see the
 * project website: http://www.jboss.org/jbpm/
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.ofbiz.jbpm;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import javolution.util.FastList;
import javolution.util.FastMap;

import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.MatchResult;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.context.def.VariableAccess;
import org.jbpm.graph.def.DelegationException;
import org.jbpm.graph.def.Transition;
import org.jbpm.taskmgmt.exe.TaskInstance;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.template.FreeMarkerWorker;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.model.ModelEntity;
import org.ofbiz.entity.model.ModelField;
import org.ofbiz.entity.model.ModelReader;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.widget.form.FormFactory;
import org.ofbiz.widget.form.FormStringRenderer;
import org.ofbiz.widget.form.ModelForm;
import org.xml.sax.SAXException;

import org.langhua.ofbiz.jbpm.workflow.util.HtmlJBPMFormRenderer;
import org.langhua.ofbiz.jbpm.workflow.util.OFBizJBPMTaskBean;
import freemarker.core.Environment;

/**
 * 
 * @author Shang Zhengwu,shangzw@langhua.cn
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public class OFBizTaskInstance implements I_OFBizTaskInstance {

	private static final String m_module = OFBizTaskInstance.class.getName();

	private HttpServletRequest m_request;

	private HttpServletResponse m_response;

	private GenericDelegator m_delegator;
	
	private LocalDispatcher m_dispatcher;

	private OFBizJBPMTaskBean m_taskBean;
	
	public OFBizTaskInstance(HttpServletRequest request, HttpServletResponse response) {

		m_request = request;
		m_response = response;
		m_delegator = (GenericDelegator) request.getAttribute("delegator");
		m_dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
		initialTaskBean();
	}
	
	public static OFBizTaskInstance getInstance(HttpServletRequest request, HttpServletResponse response) {
		return new OFBizTaskInstance(request, response);
	}
	
	protected void initialTaskBean() {
		String entryId = m_request.getParameter("taskInstanceId");
		if (UtilValidate.isNotEmpty(entryId)) {
			m_taskBean = new OFBizJBPMTaskBean(Long.parseLong(entryId));
		}
	}

	/**
	 * @see cn.langhua.ofbiz.jbpm.I_OFBizTaskInstance#getModelForm
	 */
	public String getModelForm() {
		List variableAccesses = getTaskBean().getVariableAccesses();
		for (int i=0; i<variableAccesses.size(); i++) {
			VariableAccess variableAccess = (VariableAccess) variableAccesses.get(i);
			String varialbeName = variableAccess.getVariableName();
			Perl5Compiler compiler = new Perl5Compiler();
			Perl5Matcher matcher = new Perl5Matcher();
			try {
				if (matcher.contains(varialbeName, compiler.compile(INCLUDE_FORM_REGEX))) {
					MatchResult result = matcher.getMatch();
					if (result.groups() != 3) {
						continue;
					}
					String name = result.group(1).trim();
					String location = result.group(2).trim();
					
				    ModelForm modelForm = null;
				    try {
				        modelForm = FormFactory.getFormFromLocation(location, name, ModelReader.getModelReader(getDelegator().getDelegatorName()), getDispatcher().getDispatchContext());
				    } catch (IOException e) {
				        String errMsg = "Error rendering included form named [" + name + "] at location [" + location + "]: " + e.toString();
				        Debug.logError(e, errMsg, getModule());
				        throw new RuntimeException(errMsg);
				    } catch (SAXException e) {
				        String errMsg = "Error rendering included form named [" + name + "] at location [" + location + "]: " + e.toString();
				        Debug.logError(e, errMsg, getModule());
				        throw new RuntimeException(errMsg);
				    } catch (ParserConfigurationException e) {
				        String errMsg = "Error rendering included form named [" + name + "] at location [" + location + "]: " + e.toString();
				        Debug.logError(e, errMsg, getModule());
				        throw new RuntimeException(errMsg);
				    } catch (GenericEntityException e) {
				        String errMsg = "Error rendering included form named [" + name + "] at location [" + location + "]: " + e.toString();
				        Debug.logError(e, errMsg, getModule());
				        throw new RuntimeException(errMsg);
					}

		            FormStringRenderer formStringRenderer = (FormStringRenderer) getRequest().getAttribute("formStringRenderer");
		            if (formStringRenderer == null) {
	                    formStringRenderer = new HtmlJBPMFormRenderer(getRequest(), getResponse());
		            }
		            // still null, throw an error
		            if (formStringRenderer == null) {
		                throw new IllegalArgumentException("Could not find a formStringRenderer in the context, and could not find HTTP request/response objects need to create one.");
		            }

		            StringBuffer renderBuffer = new StringBuffer();
		            
		            final Environment env = Environment.getCurrentEnvironment();
		            Map context = (Map) FreeMarkerWorker.getWrappedObject("context", env);
		            if (context == null) {
		            	context = FastMap.newInstance();
		            }
		            try {
						modelForm.renderFormString(renderBuffer, context, formStringRenderer);
					} catch (IOException e) {
				        String errMsg = "Error rendering form: " + e.toString();
				        Debug.logError(e, errMsg, getModule());
				        throw new RuntimeException(errMsg);
					}
					return renderBuffer.toString();
				}
			} catch (MalformedPatternException e) {
				// do nothing
			}
		}
		return null;
	}

	/**
	 * Finish a task instance with an assigned transition.
	 * 
	 * @return result of this finish action
	 */
	public static String finishTask(HttpServletRequest request, HttpServletResponse response) {
		return getInstance(request, response).finishTask();
	}
	
	/**
	 * @see cn.langhua.ofbiz.jbpm.I_OFBizTaskInstance#finishTask
	 */
	public String finishTask() {
		
		Map parameters = UtilHttp.getParameterMap(getRequest());
		String taskInstanceId = String.valueOf(parameters.get("taskInstanceId"));
		String transitionId = String.valueOf(parameters.get("transitionId"));
		String processInstanceId = String.valueOf(parameters.get("processInstanceId"));
		Map entities = FastMap.newInstance();
		Object[] keys = parameters.keySet().toArray();
		Perl5Compiler compiler = new Perl5Compiler();
		Perl5Matcher matcher = new Perl5Matcher();
		// filter out entities from request parameters
		for (int i=0; i<parameters.size(); i++) {
			String parameter = String.valueOf(parameters.get(keys[i]));
			try {
				if (matcher.contains(String.valueOf(keys[i]), compiler.compile(ENTITY_FORM_REGEX))) {
					MatchResult result = matcher.getMatch();
					if (result.groups() != 3) {
						// not a jbpm parameter, skip it
						continue;
					}
					String entityName = result.group(1).trim();
					String fieldName = result.group(2).trim();
					List fieldNames = (List) entities.get(entityName);
					if (fieldNames == null) {
						fieldNames = FastList.newInstance();
						fieldNames.add(fieldName);
						entities.put(entityName, fieldNames);
					} else if (!fieldNames.contains(fieldName)) {
						fieldNames.add(fieldName);
						entities.put(entityName, fieldNames);
					}
				}
			} catch (MalformedPatternException e) {
				Debug.logError(e, m_module);
			}
		}
		
		ModelReader reader = getDelegator().getModelReader();
		Object[] entityNames = entities.keySet().toArray();
		for (int i=0; i<entityNames.length; i++) {
			String entityName = String.valueOf(entityNames[i]);
			try {
				// Get pks (ModelField objects)
				ModelEntity modelEntity = reader.getModelEntity(entityName);
				List pks = modelEntity.getPksCopy();
				List nopks = modelEntity.getNopksCopy();
				
				// Fill pks from parameters
	        	Map pksFound = FastMap.newInstance();
	        	for (int j=0; j<pks.size(); j++) {
	        		ModelField pk = (ModelField) pks.get(j);
    				String parameter = String.valueOf(parameters.get(pk.getName()));
        			for (int k=0; k<parameters.size(); k++) {
        				String key = String.valueOf(keys[k]);
    	        		if (UtilValidate.isNotEmpty(parameter) && !parameter.equals("null")) {
    	        			if (key.equals(pk.getName())) {
    	        				pksFound.put(pk.getName(), parameter);
    	        				break;
    	        			}
    	        		} else {
							try {
								if (matcher.contains(key, compiler.compile(ENTITY_FORM_REGEX))) {
									MatchResult result = matcher.getMatch();
									if (result.groups() == 3&& result.group(1).trim().equals(entityName) && result.group(2).trim().equals(pk.getName())) {
										pksFound.put(pk.getName(), String.valueOf(parameters.get(key)));
										break;
									}
								}
							} catch (MalformedPatternException e) {
								Debug.logError(e, m_module);
							}
    	        		}
        			}
	        	}
				
				// Fill nopks from parameters
	        	Map nopksFound = FastMap.newInstance();
	        	for (int j=0; j<nopks.size(); j++) {
	        		ModelField nopk = (ModelField) nopks.get(j);
    				String parameter = String.valueOf(parameters.get(nopk.getName()));
        			for (int k=0; k<parameters.size(); k++) {
        				String key = String.valueOf(keys[k]);
    	        		if (UtilValidate.isNotEmpty(parameter) && !parameter.equals("null")) {
    	        			if (key.equals(nopk.getName())) {
    	        				nopksFound.put(nopk.getName(), parameter);
    	        				break;
    	        			}
    	        		} else {
							try {
								if (matcher.contains(key, compiler.compile(ENTITY_FORM_REGEX))) {
									MatchResult result = matcher.getMatch();
									if (result.groups() == 3&& result.group(1).trim().equals(entityName) && result.group(2).trim().equals(nopk.getName())) {
										nopksFound.put(nopk.getName(), String.valueOf(parameters.get(key)));
										break;
									}
								}
							} catch (MalformedPatternException e) {
								Debug.logError(e, m_module);
							}
    	        		}
        			}
	        	}

	        	// TODO: Fill pks from request attributes
				
				// TODO: Fill pks from session attributes
				
				// Get entity instance
	        	if (!pksFound.isEmpty()) {
	        		GenericValue entity = getDelegator().findByPrimaryKey(entityName, pksFound);
		        	if (entity != null) {
		        		// found the existing entity
		        		entity.putAll(nopksFound);
		        		try {
			        		entity.store();
		    			} catch (GenericEntityException e) {
		    				// TODO: recurse the entities which couldn't be stored successful
		    				Debug.logError(e, getModule());
		    			}
		        	} else {
		        		// create a new entity
		        		entity = getDelegator().makeValue(entityName, pksFound);
		        		entity.putAll(nopksFound);
		        		try {
			        		entity.create();
		    			} catch (GenericEntityException e) {
		    				// TODO: recurse the entities which couldn't be created successful
		    				Debug.logError(e, getModule());
		    			}
		        	}
	        	}
			} catch (GenericEntityException e) {
				Debug.logError(e, getModule());
			}
		}
		
		long workflowId;
		long taskId;
		try {
			taskId = Long.parseLong(taskInstanceId);
		} catch (NumberFormatException e) {
			return ModelService.RESPOND_ERROR;
		}
		GenericValue userLogin = (GenericValue) getRequest().getSession()
				.getAttribute("userLogin");
		String userName = userLogin.getString("userLoginId");
		JbpmContext jbpmContext = JbpmConfiguration.getInstance()
				.getCurrentJbpmContext();
		jbpmContext.setActorId(userName);
		TaskInstance taskInstance = jbpmContext.getTaskMgmtSession()
				.loadTaskInstance(taskId);
		List variableAccesses = null;
		workflowId = taskInstance.getTaskMgmtInstance().getProcessInstance()
				.getId();
		variableAccesses = taskInstance.getTask().getTaskController()
				.getVariableAccesses();
		if (variableAccesses != null) {
			String className = "";
			String invokeName = "";
			Iterator iterator = variableAccesses.iterator();
			while (iterator.hasNext()) {
				VariableAccess variableAccess = (VariableAccess) iterator.next();
				String mappedName = variableAccess.getMappedName();
				if (mappedName.indexOf("#") > -1) {
					if (mappedName.indexOf("class:") > -1) {
						int classpos = mappedName.indexOf("class:");
						int invokepos = mappedName.indexOf("#invoke:");
						className = mappedName.substring(
								classpos + "class:".length(), invokepos)
								.trim();
						invokeName = mappedName.substring(
								invokepos + "#invoke:".length()).trim();
						break;
					}
				}
			}

			if (UtilValidate.isNotEmpty(className)
					&& UtilValidate.isNotEmpty(invokeName)) {
				try {
					invoke(className, invokeName, getRequest(), getResponse());
				} catch (GeneralException e) {
					Debug.logWarning(e, m_module);
				}
			} else {
				Iterator iter = variableAccesses.iterator();
				while (iter.hasNext()) {
					VariableAccess variableAccess = (VariableAccess) iter
							.next();
					String mappedName = variableAccess.getMappedName();
					try {
						if (matcher.contains(mappedName, compiler.compile(INCLUDE_FORM_REGEX))) {
							continue;
						}
					} catch (MalformedPatternException e) {
						// do nothing
					}
					if (variableAccess.isWritable()) {
						if (mappedName.indexOf("#") > -1)
							continue;
						int pos = mappedName.indexOf(".");
						if (pos < 0)
							continue;
						String entityName = mappedName.substring(0, pos);
						String parameter = mappedName.substring(pos + 1);
						String value = getRequest().getParameter(parameter);
						
						if (UtilValidate.isEmpty(value))
							value = "";
						List validWorkflows = null;
						try {
							List constraints = new LinkedList();
							constraints.add(new EntityExpr(
									OFBizNewProcessInstance.WORKFLOW_ID,
									EntityOperator.EQUALS, "" + workflowId));
							validWorkflows = getDelegator().findByAnd(entityName,
									constraints, null);
						} catch (GenericEntityException e) {
							Debug.logWarning(e, m_module);
						}
						if (validWorkflows == null || validWorkflows.size() < 0) {
							// do nothing
							continue;
						}
						try {
							getDelegator().storeByCondition(entityName, UtilMisc
									.toMap(parameter, value), new EntityExpr(
									OFBizNewProcessInstance.WORKFLOW_ID,
									EntityOperator.EQUALS, "" + workflowId));
						} catch (GenericEntityException e) {
							Debug.logWarning(e, m_module);
						}
					} else {
						continue;
					}
				}
			}
		}
		if (UtilValidate.isNotEmpty(transitionId)) {
			try {
				List transitions = taskInstance.getAvailableTransitions();
				for (int i = 0; i < transitions.size(); i++) {
					Transition transition = (Transition) transitions.get(i);
					if (transitionId.equals(String.valueOf(transition.getId()))) {
						taskInstance.end(transition);
						break;
					}
				}
			} catch (DelegationException e) {
				Debug.logError(e, getModule());
			}
		} else {
			taskInstance.end();
		}
		jbpmContext.save(taskInstance);

		return ModelService.RESPOND_SUCCESS;
	}

	public HttpServletRequest getRequest() {
		return m_request;
	}

	public HttpServletResponse getResponse() {
		return m_response;
	}

	public GenericDelegator getDelegator() {
		return m_delegator;
	}

	public void setDelegator(GenericDelegator delegator) {

		m_delegator = delegator;
	}

	public void setDelegator(String delegatorName) {

		if (UtilValidate.isNotEmpty(delegatorName))
			m_delegator = GenericDelegator
					.getGenericDelegator(delegatorName);
	}
	
	public OFBizJBPMTaskBean getTaskBean() {
		return m_taskBean;
	}
	
	public void setTaskBean(OFBizJBPMTaskBean taskBean) {
		m_taskBean = taskBean;
	}
	
	public String getModule() {
		return m_module;
	}
	
	public LocalDispatcher getDispatcher() {
		return m_dispatcher;
	}

	public static String invoke(String eventPath, String eventMethod,
			HttpServletRequest request, HttpServletResponse response)
			throws GeneralException {
		Class eventClass = null;
		try {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			eventClass = loader.loadClass(eventPath);
		} catch (ClassNotFoundException e) {
			Debug.logError(e, "Error loading class with name: " + eventPath
					+ ", will not be able to run event...", m_module);
		}
		if (Debug.verboseOn())
			Debug.logVerbose("[Set path/method]: " + eventPath + " / "
					+ eventMethod, m_module);
		Class[] paramTypes = new Class[] { HttpServletRequest.class,
				HttpServletResponse.class };

		Debug.logVerbose("*[[Event invocation]]*", m_module);
		Object[] params = new Object[] { request, response };

		return invoke(eventPath, eventMethod, eventClass, paramTypes, params);
	}

	private static String invoke(String eventPath, String eventMethod,
			Class eventClass, Class[] paramTypes, Object[] params)
			throws GeneralException {
		if (eventClass == null) {
			throw new GeneralException("Error invoking event, the class "
					+ eventPath + " was not found");
		}
		if (eventPath == null || eventMethod == null) {
			throw new GeneralException(
					"Invalid event method or path; call initialize()");
		}

		Debug.logVerbose("[Processing]: JAVA Event", m_module);
		try {
			Method m = eventClass.getMethod(eventMethod, paramTypes);
			String eventReturn = (String) m.invoke(null, params);

			if (Debug.verboseOn())
				Debug.logVerbose("[Event Return]: " + eventReturn, m_module);
			return eventReturn;
		} catch (java.lang.reflect.InvocationTargetException e) {
			Throwable t = e.getTargetException();

			if (t != null) {
				Debug.logError(t, "Problems Processing Event", m_module);
				throw new GeneralException("Problems processing event: "
						+ t.toString(), t);
			} else {
				Debug.logError(e, "Problems Processing Event", m_module);
				throw new GeneralException("Problems processing event: "
						+ e.toString(), e);
			}
		} catch (Exception e) {
			Debug.logError(e, "Problems Processing Event", m_module);
			throw new GeneralException("Problems processing event: "
					+ e.toString(), e);
		}
	}
}
