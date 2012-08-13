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
package org.langhua.ofbiz.jbpm.workflow.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Document;
import org.hibernate.Query;
import org.hibernate.Session;
import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.JbpmException;
import org.jbpm.context.exe.VariableInstance;
import org.jbpm.db.GraphSession;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.graph.exe.Token;
import org.jbpm.taskmgmt.exe.TaskInstance;

/**
 * Contains the data of a process.<p>
 *  
 * @author Shi Yusen, shiys@langhua.cn  
 * @author Shang Zhengwu,shangzw@langhua.cn
 * 
 */
public class OFBizJBPMProcessBean extends Object {
    
    /** The process name. */
    private String processName;
    
    /** The process instance id. */
    private long id;

    private int processDefinitionVersion;
    private long processDefinitionId;
    private Date start;
    private Date end;
    
    private Document config;

    private ArrayList tokens;
    private ArrayList variables;
    private ArrayList tasks;
    private long tokenInstanceId;
    private String comment;

    /**
     * Constructs a new, empty process bean.<p>
     */
    public OFBizJBPMProcessBean(){
    	
        processName = new String();
        id = -1;
    }

    /**
     * Constructs a new process bean.<p>
     * 
     */
    public OFBizJBPMProcessBean(long id) {

        this.id = id;
        if (id > -1) {
            initialize();
        }
    }

    public OFBizJBPMProcessBean( 
    		long id, String processName, int processDefinitionVersion,
    		long processDefinitionId, Date start, Date end, Document config, ArrayList tokens, ArrayList variables,
    		ArrayList tasks, long tokenInstanceId,List resources,String comment) {
    	
    	this.id = id;
    	this.processName = processName;
    	this.processDefinitionVersion = processDefinitionVersion;
    	this.processDefinitionId = processDefinitionId;
    	this.start = start;
    	this.end = end;
    	this.config = config;
    	this.tokens = tokens;
    	this.variables = variables;
    	this.tasks = tasks;
    	this.tokenInstanceId = tokenInstanceId;
    	this.resources=resources;
    	this.comment =comment;
	}

	private void initialize() {
        JbpmContext jbpmContext = JbpmConfiguration.getInstance().getCurrentJbpmContext();
        if (jbpmContext == null) {
        	jbpmContext = JbpmConfiguration.getInstance().createJbpmContext();
		}
        GraphSession graphSession = jbpmContext.getGraphSession();
        ProcessInstance processInstance = graphSession.loadProcessInstance(id);
        processName = processInstance.getProcessDefinition().getName();
        start = processInstance.getStart();
        end = processInstance.getEnd();
        processDefinitionId = processInstance.getProcessDefinition().getId();
        processDefinitionVersion = processInstance.getProcessDefinition().getVersion();

        initializeVariablesList(processInstance);
        initializeTokensList(processInstance);
        initializeTasksList(processInstance);
    }

    private void initializeVariablesList(ProcessInstance processInstance) {

        // Variables list
        variables = new ArrayList();

        JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
        if (context == null) {
			context = JbpmConfiguration.getInstance().createJbpmContext();
		}
        // add the task instances which actorid is null
        // add the tasks of userName's groupid or pooledid
        Session session = context.getSession();
        try {
            String queryString = "select ti " +
                    "from org.jbpm.context.exe.VariableInstance as ti " +
                    "where ti.token = :token and ti.processInstance = :processInstance";
            Query query = session.createQuery(queryString);
            query.setLong("token", processInstance.getRootToken().getId());
            query.setLong("processInstance", processInstance.getId());
            List result = query.list();
            for (int i=0; i<result.size(); i++) {
                VariableInstance variableInstance = (VariableInstance) result.get(i);
                variables.add(new OFBizJBPMVariableBean(variableInstance.getName(), variableInstance.getValue()));
            }
        } catch (Exception e) {
            throw new JbpmException("couldn't get variable instances list for process " + processInstance.getId(), e);
        }
        
    }

    private void initializeTokensList(ProcessInstance processInstance) {

        // Tokens list
        Token rootToken = processInstance.getRootToken();

        tokens = new ArrayList();
        tokenInstanceId = rootToken.getId();
        if (rootToken != null && rootToken.getNode() != null) {
            tokens.add(new OFBizJBPMTokenBean(rootToken.getId(), "Root", rootToken.getNode().getName(), rootToken.getNode().getClass().getName(), rootToken.getStart(),
                    rootToken.getEnd(), 1));
            try {
                  if (rootToken.getChildren().isEmpty() == false) {
                    AddChildrenTokensToTokensList(tokens, rootToken, 2);
                  }
            } catch (Exception exception) {
            	// do nothing
            }
        }
    }

    /**
     * 
     * Add token childs to the current token beans list
     * 
     * @param tokensList
     *          Current token list to update
     * @param token
     *          Token where are the token childs
     * @param level
     *          Level where is the token: 1 for the root token, 2 for the childs
     *          of the root token, ...
     */
    private void AddChildrenTokensToTokensList(ArrayList tokensList, Token token, long level) {

        Iterator childrenIterator = token.getChildren().values().iterator();
        while (childrenIterator.hasNext()) {
            Token childToken = (Token) childrenIterator.next();
            tokensList.add(new OFBizJBPMTokenBean(childToken.getId(), childToken.getName(), childToken.getNode().getName(), childToken.getNode().getClass().getName(),
                    childToken.getStart(), childToken.getEnd(), level));
            try {
                if (childToken.getChildren().isEmpty() == false) {
                    AddChildrenTokensToTokensList(tokensList, childToken, level + 1);
                }
            } catch (Exception exception) {
            }
        }
    }

    private void initializeTasksList(ProcessInstance processInstance) {

        // Tasks list
        tasks = new ArrayList();
        if (processInstance.getTaskMgmtInstance().getTaskInstances().isEmpty() == false) {
            Iterator tasksIterator = processInstance.getTaskMgmtInstance().getTaskInstances().iterator();
            while (tasksIterator.hasNext()) {
                TaskInstance taskInstance = (TaskInstance) tasksIterator.next();
                tasks.add(taskInstance);
            }
        }
    }


    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {

        if (obj == this) {
            return true;
        }
        if (obj instanceof OFBizJBPMProcessBean) {
            return ((OFBizJBPMProcessBean)obj).id == id;
        }
        return false;
    }

    /**
     * Returns the process name.<p>
     * 
     * @return the process name
     * 
     */
    public String getProcessName() {

        return processName;
    }
    
    public long getId() {
        return id;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {

        return getProcessName().hashCode();
    }

    /**
     * Sets the process name.<p>
     * 
     * @param processName the process name
     * 
     */
    public void setProcessName(String processName) {

        this.processName = processName;
    }

    public void setId(long id) {
        this.id = id;
    }
    
    /**
     * @see java.lang.Object#toString()
     */
    public String toString() {

        return "[" + getClass().getName() + ", process instance id: " + id + "]";
    }

    public Date getStart() {
        return start;
    }

    public void setStart(Date start) {
        this.start = start;
    }

    public Date getEnd() {
        return end;
    }

    public void setEnd(Date end) {
        this.end = end;
    }

    public ArrayList getTokens() {
        return tokens;
    }

    public void setTokens(ArrayList tokens) {
        this.tokens = tokens;
    }

    public int getProcessDefinitionVersion() {
        return processDefinitionVersion;
    }

    public void setProcessDefinitionVersion(int processDefinitionVersion) {
        this.processDefinitionVersion = processDefinitionVersion;
    }

    public long getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(long processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public ArrayList getVariables() {
        return variables;
    }

    public void setVariables(ArrayList  variables) {
        this.variables = variables;
    }

    public ArrayList getTasks() {
        return tasks;
    }

    public void setTasks(ArrayList tasks) {
        this.tasks = tasks;
    }

    public long getTokenInstanceId() {
        return tokenInstanceId;
    }

    public void setTokenInstanceId(long tokenInstanceId) {
        this.tokenInstanceId = tokenInstanceId;
    }

    public Document getConfig() {
        return config;
    }

    public void setConfig(Document config) {
        this.config = config;
    }

    /**
     * @see java.lang.Object#clone()
     */
    public Object clone() {

        // create a copy of the jbpm process bean
        OFBizJBPMProcessBean result = new OFBizJBPMProcessBean(
        		  id,
        		  processName,
        		  processDefinitionVersion,
        		  processDefinitionId,
        		  start,
        		  end,
        		  config,
        		  tokens,
        		  variables,
        		  tasks,
        		  tokenInstanceId,
        		  resources,
        		  comment
        		  );
        return result;
    }
    
    private List resources;
    
    public String getComment(){
        return comment;
    }
}