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

package org.langhua.ofbiz.jbpm.report;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import javax.xml.parsers.ParserConfigurationException;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilXml;
import org.ofbiz.entity.GenericDelegator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import org.langhua.ofbiz.jbpm.workflow.util.OFBizUserToJBPMUser;
import org.langhua.ofbiz.report.A_OFBizReportThread;
import org.langhua.ofbiz.report.I_OFBizReport;
import org.langhua.ofbiz.report.util.ReportStringUtil;

/**
 * Thread for running jbpm html report. <p>
 * 
 * @author Shi Yusen, shiys@langhua.cn
 *  
 */
public class JBPMHtmlThread extends A_OFBizReportThread {

	public static final String SHOW_CREATE_SCRIPTS = "showcreatescripts";
	
	public static final String CREATE_TABLES = "createtables";
	
	public static final String SHOW_DROP_SCRIPTS = "showdropscripts";
	
	public static final String DROP_TABLES = "droptables";
	
	public static final String SYNC_USERS = "synchronize";
	
	public static final String CREATE_TABLES_SQLFILENAME = "create.sql";

	public static final String DROP_TABLES_SQLFILENAME = "drop.sql";
	
	protected String m_database;
	
	protected String m_datasource;
	
	protected GenericDelegator m_delegator;

	/**
     * Constructor, creates a new JBPMHtmlThread.<p>
     * 
     * @param request the HttpServletRequest
     * @param response the HttpServletResponse
     * @param name the thread name prefix
     */
    public JBPMHtmlThread(HttpServletRequest request, HttpServletResponse response, String name) {

        super(request, response, name);
        initHtmlReport(request, response);
        m_delegator = (GenericDelegator) request.getAttribute("delegator");
    }

    /**
     * @see cn.langhua.ofbiz.report.A_OFBizReportThread#getReportUpdate()
     */
    public String getReportUpdate() {

        return getReport().getReportUpdate();
    }

    /**
     * @see java.lang.Runnable#run()
     */
    public void run() {

        try {
            if (getName().startsWith(SHOW_CREATE_SCRIPTS)) {
            	// Show the sql scripts to create tables
            	boolean canGetConfiguration = getDBConfiguration();
            	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_DISPLAY_SCRIPTS_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
            	if (canGetConfiguration) {
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "DATABASE_TYPE_1", new Object[] {m_database}, getLocale()), I_OFBizReport.FORMAT_HEADLINE);
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "DATASOURCE_1", new Object[] {m_datasource}, getLocale()), I_OFBizReport.FORMAT_HEADLINE);
                	getReport().println();
                	String content = getSQLScripts(m_database, CREATE_TABLES_SQLFILENAME);
                    if (ReportStringUtil.isNotEmpty(content)) {
                        displayScript(content);
                    }
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_DISPLAYSCRIPT_COMPLETED_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
            	} else {
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "CANNOT_GET_CONFIGURATION_0", getLocale()), I_OFBizReport.FORMAT_ERROR);
            	}
            } else if (getName().startsWith(CREATE_TABLES)) {
            	// Run the sql scripts to create tables
            	boolean canGetConfiguration = getDBConfiguration();
            	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "RUN_CREATE_SCRIPTS_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
            	if (canGetConfiguration) {
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "DATABASE_TYPE_1", new Object[] {m_database}, getLocale()), I_OFBizReport.FORMAT_HEADLINE);
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "DATASOURCE_1", new Object[] {m_datasource}, getLocale()), I_OFBizReport.FORMAT_HEADLINE);
                	getReport().println();
                	String content = getSQLScripts(m_database, CREATE_TABLES_SQLFILENAME);
                    if (ReportStringUtil.isNotEmpty(content)) {
                        boolean result = runScript(content);
                        if (result) {
                        	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_CREATETABLE_FINISHED_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
                        } else {
                        	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_NO_CONNNECT_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
                        }
                    }
            	} else {
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "CANNOT_GET_CONFIGURATION_0", getLocale()), I_OFBizReport.FORMAT_ERROR);
            	}
            } else if (getName().startsWith(SHOW_DROP_SCRIPTS)) {
            	// Show the sql scripts to create tables
            	boolean canGetConfiguration = getDBConfiguration();
            	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_DISPLAY_SCRIPTS_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
            	if (canGetConfiguration) {
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "DATABASE_TYPE_1", new Object[] {m_database}, getLocale()), I_OFBizReport.FORMAT_HEADLINE);
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "DATASOURCE_1", new Object[] {m_datasource}, getLocale()), I_OFBizReport.FORMAT_HEADLINE);
                	getReport().println();
                	String content = getSQLScripts(m_database, DROP_TABLES_SQLFILENAME);
                    if (ReportStringUtil.isNotEmpty(content)) {
                        displayScript(content);
                    }
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_DISPLAYSCRIPT_COMPLETED_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
            	} else {
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "CANNOT_GET_CONFIGURATION_0", getLocale()), I_OFBizReport.FORMAT_ERROR);
            	}
            } else if (getName().startsWith(DROP_TABLES)) {
            	// Run the sql scripts to create tables
            	boolean canGetConfiguration = getDBConfiguration();
            	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "RUN_DROP_SCRIPTS_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
            	if (canGetConfiguration) {
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "DATABASE_TYPE_1", new Object[] {m_database}, getLocale()), I_OFBizReport.FORMAT_HEADLINE);
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "DATASOURCE_1", new Object[] {m_datasource}, getLocale()), I_OFBizReport.FORMAT_HEADLINE);
                	getReport().println();
                	String content = getSQLScripts(m_database, DROP_TABLES_SQLFILENAME);
                    if (ReportStringUtil.isNotEmpty(content)) {
                        boolean result = runScript(content);
                        if (result) {
                        	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_DROPTABLE_FINISHED_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
                        } else {
                        	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_NO_CONNNECT_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
                        }
                    }
            	} else {
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "CANNOT_GET_CONFIGURATION_0", getLocale()), I_OFBizReport.FORMAT_ERROR);
            	}
            } else if (getName().startsWith(SYNC_USERS)) {
            	// Run the sql scripts to create tables
            	boolean canGetConfiguration = getDBConfiguration();
            	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_SYNCHRONIZE_USERS_START_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
            	if (canGetConfiguration) {
            		boolean result = OFBizUserToJBPMUser.syncUsersToUser(m_delegator, getReport(), getLocale());
            		if (result) {
                    	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_SYNCHRONIZE_USERS_FINISH_SUCCESS_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
            		} else {
                    	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_SYNCHRONIZE_USERS_FINISH_ERROR_0", getLocale()), I_OFBizReport.FORMAT_ERROR);
            		}
            	} else {
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "CANNOT_GET_CONFIGURATION_0", getLocale()), I_OFBizReport.FORMAT_ERROR);
            	}
        	} else {
            	getReport().println(getName(), I_OFBizReport.FORMAT_ERROR);
            }
        } catch (Exception e) {
            getReport().println(e);
            if (Debug.errorOn()) {
                Debug.log(e);
            }
        }
    }
    
    protected boolean getDBConfiguration() {
    	
    	FileInputStream configFileIS = null;
    	try {
        	File jbpm_config = new File("specialpurpose/jbpm/config/jbpm-config.xml");
        	configFileIS = new FileInputStream(jbpm_config);
        	Document configDoc = UtilXml.readXmlDocument(configFileIS, "jBPM configuration file specialpurpose/jbpm/config/jbpm-config.xml");
        	Element rootElement = configDoc.getDocumentElement();
        	m_database = UtilXml.childElementValue(rootElement, "database", null);
        	m_datasource = UtilXml.childElementValue(rootElement, "datasource", null);
        	return true;
    	} catch (FileNotFoundException e) {
			e.printStackTrace();
    	} catch (SAXException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (configFileIS != null) {
				try {
					configFileIS.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return false;
    }
    
    
    protected String getSQLScripts(String database, String sqlFilename) {
    	
    	if (ReportStringUtil.isEmptyOrWhitespaceOnly(database)) {
    		return "";
    	}
    	
    	FileInputStream scriptFileIS = null;
    	try {
        	File scriptFile = new File("specialpurpose/jbpm/db/" + database + "/" + sqlFilename);
        	scriptFileIS = new FileInputStream(scriptFile);
            byte[] bytes = new byte[(int) scriptFile.length()];
            scriptFileIS.read(bytes, 0, (int) scriptFile.length() - 1);
        	String scriptContent = new String(bytes);
        	return scriptContent;
    	} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (scriptFileIS != null) {
				try {
					scriptFileIS.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
    	}
		
		return "";
    }
    
    /**
     * Run the script in datasource.
     * 
     * @param script
     * @return true if the script runs successfully, else false.
     */
    private boolean runScript(String script) {
        
        if (ReportStringUtil.isNotEmpty(script)) {
            Connection connection = null;
            DataSource dataSource = null;
            if (ReportStringUtil.isEmpty(m_datasource)) {
                try {
                    connection = DriverManager.getConnection(m_datasource);
                } catch (SQLException e) {
                    getReport().println(e);
                }
            } else {
                InitialContext context = null;
                try {
                    context = new InitialContext();
                } catch(Exception e) {
                	if (Debug.errorOn()) {
                        Debug.log(e);
                    }
                }
                
                if (dataSource == null && context != null) {
                    try {
                    	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_TRYTOFIND_DATASOURCE_1", new Object[] {m_datasource}, getLocale()));
                        dataSource = (DataSource) context.lookup(m_datasource);
                    } catch (NamingException e) {
                        getReport().println(new Throwable(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_DATASOURCE_NOTEXISTS_0", getLocale())));
                        dataSource = null;
                    }
                    // tomcat JNDI
                    if (dataSource == null && !m_datasource.startsWith("java:")) {
                        try {
                        	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_TRYTOFIND_DATASOURCE_1", new Object[] {"java:comp/env/" + m_datasource}, getLocale()));
                            dataSource = (DataSource) context.lookup("java:comp/env/" + m_datasource);
                        } catch (NamingException e) {
                            getReport().println(new Throwable(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_DATASOURCE_NOTEXISTS_0", getLocale())));
                            dataSource = null;
                        }
                    }
                }

                if (dataSource != null) {
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_DATASOURCE_EXISTS_0", getLocale()));
                	getReport().println();
                	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_TRYTOCONNECT_DATASOURCE_0", getLocale()));
                    try {
                        connection = dataSource.getConnection();
                    } catch (SQLException e) {
                        getReport().println(e.getLocalizedMessage(), I_OFBizReport.FORMAT_ERROR);
                    }
                }
            }

            if (connection != null) {
                // database exists
                PreparedStatement statement = null;
                script = script.replaceAll("[#]+.*[\n\r]", "");
                String[] queries = script.split(";");
                for (int i=0; i<queries.length; i++) {
                	if (ReportStringUtil.isEmpty(queries[i].trim()))
                		continue;
                    try {
                        statement = connection.prepareStatement(queries[i].trim());
                    	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_STARTTORUN_SCRIPT_0", getLocale()));
                        getReport().println(queries[i].trim().replaceAll("[\n\r]+", "<br>"), I_OFBizReport.FORMAT_NOTE);
                        statement.execute();
                    	getReport().println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_SCRIPT_RUNOK_0", getLocale()), I_OFBizReport.FORMAT_OK);
                    } catch (SQLException e) {
                        getReport().println(e.getLocalizedMessage(), I_OFBizReport.FORMAT_ERROR);
                    }
                }
                try {
                    statement.close();
                } catch (SQLException e) {
                    getReport().println(e);
                }
                try {
                    connection.close();
                } catch (SQLException e) {
                    getReport().println(e);
                }
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    /**
     * Display the script.
     * 
     * @param script
     */
    private void displayScript(String script) {
        
        if (ReportStringUtil.isNotEmpty(script)) {
            script = script.replaceAll("[#]+.*[\n\r]", "");
            String[] queries = script.split(";");
            for (int i=0; i<queries.length; i++) {
                getReport().println(queries[i].trim().replaceAll("[\n\r]+", "<br>"), I_OFBizReport.FORMAT_NOTE);
            	getReport().println();
            }
        }
    }
}
