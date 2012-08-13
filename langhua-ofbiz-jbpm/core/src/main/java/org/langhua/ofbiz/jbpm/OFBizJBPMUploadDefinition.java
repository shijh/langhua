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

import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.graph.def.ProcessDefinition;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.DiskFileUpload;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadException;

/**
 * Class to upload a jBPM workflow definition with HTTP upload.
 * <p>
 * 
 * @author Shang Zhengwu,shangzw@langhua.cn
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public class OFBizJBPMUploadDefinition {
	
	public static final String module = OFBizJBPMUploadDefinition.class.getName();

	public OFBizJBPMUploadDefinition() {
	}

	/**
	 * commit request.
	 */
	public static boolean actionCommit(HttpServletRequest request)
			throws IOException, ServletException {

		String maxSizeStr = UtilProperties.getPropertyValue(
				"general.properties", "http.upload.max.size", "-1");
		long maxUploadSize = -1;
		try {
			maxUploadSize = Long.parseLong(maxSizeStr);
		} catch (NumberFormatException e) {
			Debug
					.logError(
							e,
							"Unable to obtain the max upload size from general.properties; using default -1",
							OFBizJBPMUploadDefinition.class.getName());
			maxUploadSize = -1;
		}
		String encoding = request.getCharacterEncoding();
		boolean isMultiPart = FileUpload.isMultipartContent(request);

		if (isMultiPart) {
			DiskFileUpload upload = new DiskFileUpload();
			if (encoding != null) {
				upload.setHeaderEncoding(encoding);
			}
			upload.setSizeMax(maxUploadSize);

			List uploadedItems = null;
			try {
				uploadedItems = upload.parseRequest(request);
			} catch (FileUploadException e) {
				e.printStackTrace();
			}
			if (uploadedItems != null) {
				Iterator i = uploadedItems.iterator();

				FileItem fi = null;
				while (i.hasNext()) {
					fi = (FileItem) i.next();
					if (fi.getName() != null) {
						// found the file object, leave iteration
						break;
					} else {
						// this is no file object, check next item
						continue;
					}
				}

				if (fi != null
						&& UtilValidate.isNotEmpty(fi.getName())
						&& (fi.getContentType().indexOf(
								"application/x-zip-compressed") != -1 || fi
								.getContentType().indexOf(
										"application/octet-stream") != -1)) {
					// file name has been specified, upload the file
					// redirect
					try {
						ZipInputStream zipInputStream = new ZipInputStream(fi
								.getInputStream());
						JbpmContext context = JbpmConfiguration.getInstance()
								.getCurrentJbpmContext();
						if (context == null) {
							context = JbpmConfiguration.getInstance()
									.createJbpmContext();
						}
						if (context == null) {
							Debug.logInfo("JbpmContext is null.",module);
							return false;
						}
						ProcessDefinition processDefinition = ProcessDefinition
								.parseParZipInputStream(zipInputStream);
						Debug.logInfo("Created a processdefinition : "
								+ processDefinition.getName(),
								module);
						context.deployProcessDefinition(processDefinition);
						zipInputStream.close();
					} catch (IOException e) {
						Debug.logError(e.getMessage(), module);
					}
				}
			} else {
				return false;
			}
			return true;
		}
		return false;
	}
}