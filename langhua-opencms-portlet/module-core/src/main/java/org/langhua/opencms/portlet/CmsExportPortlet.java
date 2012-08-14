/*
 * This library is part of OpenCms-Portlet module
 *
 * Copyright (C) 2009 Langhua Opensource Foundation (http://langhua.org)
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
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * For further information about OpenCms-Portlet, please see the 
 * project website: http://langhua.org/opencms/
 *  
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.opencms.portlet;

import org.opencms.file.CmsResource;
import org.opencms.jsp.CmsJspActionElement;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsRequestUtil;
import org.opencms.util.CmsStringUtil;
import org.opencms.workplace.CmsDialog;
import org.opencms.workplace.CmsWorkplaceSettings;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import org.apache.commons.logging.Log;

/**
 * Provides methods for the export porlet dialog.
 * <p>
 * 
 * The following files use this class:
 * <ul>
 * <li>/commons/exportportlet.jsp
 * </ul>
 * <p>
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public class CmsExportPortlet extends CmsDialog {

	/** Value for the action: export the portlet war. */
	public static final int ACTION_EXPORTPORTLET = 50;

	public static final String PARAM_WARNAME = "warfilename";
	
	public static final String PARAM_RFSPATH="rfspath";

	/** The dialog type. */
	public static final String DIALOG_TYPE = "exportportlet";
	
	public static final String PORTLET_EXPORT_REPORT = "/system/workplace/commons/report-exportportlet.jsp";

	/** The log object for this class. */
	private static final Log LOG = CmsLog.getLog(CmsExportPortlet.class);	

	private String m_paramWarFilename;

	private String m_paramRfsPath;
	
	/**
	 * Public constructor with JSP action element.
	 * <p>
	 * 
	 * @param jsp
	 *            an initialized JSP action element
	 */
	public CmsExportPortlet(CmsJspActionElement jsp) {

		super(jsp);
	}
	/**
	 * Public constructor with JSP variables.
	 * <p>
	 * 
	 * @param context 
	 *            the JSP page context
	 * @param req
	 *            the JSP request
	 * @param res
	 *            the JSP response
	 */
	public CmsExportPortlet(PageContext context, HttpServletRequest req,
			HttpServletResponse res) {

		this(new CmsJspActionElement(context, req, res));
	}

	public void actionExportPortlet() throws JspException {
		
		Map<String, String> param = new HashMap<String, String>();
		param.put(PARAM_MESSAGE, DIALOG_TYPE);
		param.put(PARAM_WARNAME, CmsStringUtil.isEmpty(m_paramWarFilename) ? "" : m_paramWarFilename);
		param.put(PARAM_RFSPATH, CmsStringUtil.isEmpty(m_paramRfsPath) ? "" : m_paramRfsPath);
		param.put(PARAM_RESOURCE, CmsStringUtil.isEmpty(getParamResource()) ? "" : getParamResource());
		
		try {
			getToolManager().jspForwardPage(this, PORTLET_EXPORT_REPORT, param);
		} catch (IOException e1) {
			if (LOG.isErrorEnabled()) {
				LOG.error(e1);
			}
		} catch (ServletException e1) {
			if (LOG.isErrorEnabled()) {
				LOG.error(e1);
			}
		}
		if (getAction() != ACTION_CANCEL) {
			setAction(ACTION_DEFAULT);
		}
	}

	public String getCurrentResourceName() {

		String resourceName = CmsResource.getName(getParamResource());
		if (resourceName.endsWith("/")) {
			resourceName = resourceName.substring(0, resourceName.length() - 1);
		}
		return resourceName;
	}

	public String getParamWarFilename() {

		return m_paramWarFilename;
	}

	public void setParamWarFilename(String filename) {

		m_paramWarFilename = filename;
	}

	public String getParamRfsPath() {

		return m_paramRfsPath;
	}

	public void setParamRfsPath(String path) {

		m_paramRfsPath = path;
	}

	/**
	 * @see org.opencms.workplace.CmsWorkplace#initWorkplaceRequestValues(org.opencms.workplace.CmsWorkplaceSettings,
	 *      javax.servlet.http.HttpServletRequest)
	 */
	protected void initWorkplaceRequestValues(CmsWorkplaceSettings settings,
			HttpServletRequest request) {

		super.initWorkplaceRequestValues(settings, request);
		// set the dialog type
		setParamDialogtype(DIALOG_TYPE);
		// set the action for the JSP switch
		if (DIALOG_TYPE.equals(getParamAction())) {
			setAction(ACTION_EXPORTPORTLET);
		} else if (DIALOG_CANCEL.equals(getParamAction())) {
			setAction(ACTION_CANCEL);
		} else {
			setAction(ACTION_DEFAULT);
			setParamTitle(key(Messages.GUI_EXPORT_PORTLET_RESOURCE_0));
		}
	}

	/**
	 * Performs the portlet export.
	 * <p>
	 * 
	 * @return true, if the portlet was export, otherwise false
	 * @throws CmsException
	 *             if export is not successful
	 */
	protected boolean performDialogOperation() throws CmsException {
		
		return true;
	}

	public void actionCloseDialog() throws JspException {

        getSettings().setListObject(null);      
        // create a map with empty "resource" parameter to avoid changing the folder when returning to explorer file list
        Map<String, String> params = new HashMap<String, String>();
        params.put(PARAM_RESOURCE, "");
        if (isPopup()) {
            try {
                // try to close the popup
                JspWriter out = getJsp().getJspContext().getOut();
                out.write("<html><head></head>\n");
                out.write("<body onload=\"top.close();\">\n");
                out.write("</body>\n");
                out.write("</html>\n");
            } catch (IOException e) {
                // error redirecting, include explorer file list
                getJsp().include(FILE_EXPLORER_FILELIST, null, params);
            }
        } else if (getParamCloseLink() != null) {
            // close link parameter present
            try { 
                if (Boolean.valueOf(getParamRedirect()).booleanValue()) {
                    // redirect parameter is true, redirect to given close link                 
                    getJsp().getResponse().sendRedirect(getParamCloseLink());
                } else {
                    // forward JSP
                    if (!isForwarded()) {
                        setForwarded(true);
                        CmsRequestUtil.forwardRequest(
                            getParamCloseLink(),
                            getJsp().getRequest(),
                            getJsp().getResponse());                         
                    }
                }
            } catch (Exception e) {
                throw new JspException(e.getMessage(), e);
            }
        } else if (getParamFramename() != null) { 
            String frameUri = (String)getSettings().getFrameUris().get(getParamFramename());
            if (frameUri != null) {
                if (frameUri.startsWith(OpenCms.getSystemInfo().getOpenCmsContext())) {
                    frameUri = frameUri.substring(OpenCms.getSystemInfo().getOpenCmsContext().length());
                }
                getJsp().include(frameUri, null, params);   
            } else {
                getJsp().include(FILE_EXPLORER_FILELIST, null, params);
            }
        } else { 
            getJsp().include(FILE_EXPLORER_FILELIST, null, params);
        }    
    }

}
