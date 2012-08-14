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

import org.opencms.i18n.A_CmsMessageBundle;
import org.opencms.i18n.I_CmsMessageBundle;

/**
 * Convenience class to access the localized messages of this package.<p> 
 * 
 */
public final class Messages extends A_CmsMessageBundle {

    /** Name of the used resource bundle. */
    private static final String BUNDLE_NAME = "org.langhua.opencms.portlet.messages";

    /** Static instance member. */
    private static final I_CmsMessageBundle INSTANCE = new Messages();

    public static final String GUI_EXPORT_PORTLET_FOLDERTO_0 = "GUI_EXPORT_PORTLET_FOLDERTO_0"; 
    public static final String GUI_EXPORT_PORTLET_WARFILENAME_0 = "GUI_EXPORT_PORTLET_WARFILENAME_0"; 
    public static final String GUI_EXPLORER_CONTEXT_PORTLETEXPORT_0   ="GUI_EXPLORER_CONTEXT_PORTLETEXPORT_0";
	public static final String GUI_EXPORT_PORTLET_RESOURCE_0 = "GUI_EXPORT_PORTLET_RESOURCE_0";

	public static final String fileicon_jboss_portal = "fileicon_jboss_portal";
	public static final String fileicon_jboss_portlet = "fileicon_jboss_portlet";
	public static final String fileicon_jboss_portlet_instances = "fileicon_jboss_portlet_instances";
	public static final String fileicon_jboss_portlet_object = "fileicon_jboss_portlet_object";

	public static final String GUI_EXPORT_PORTLET_NORFSPATH_1 = "GUI_EXPORT_PORTLET_NORFSPATH_1";
	public static final String GUI_EXPORT_PORTLET_RFSPATH_1 = "GUI_EXPORT_PORTLET_RFSPATH_1";
	public static final String GUI_EXPORT_PORTLET_NOWARNAME_0 = "GUI_EXPORT_PORTLET_NOWARNAME_0";
	public static final String GUI_EXPORT_PORTLET_START_2 = "GUI_EXPORT_PORTLET_START_2";
	public static final String GUI_EXPORT_PORTLET_NORESOURCE_0 = "GUI_EXPORT_PORTLET_NORESOURCE_0";
	public static final String GUI_EXPORT_PORTLET_NOWARNAME_1="GUI_EXPORT_PORTLET_NOWARNAME_1";
	public static final String GUI_EXPORT_PORTLET_END_0 = "GUI_EXPORT_PORTLET_END_0";

	public static final String RPT_VALIDATING_XML_0 = "RPT_VALIDATING_XML_0";

	public static final String RPT_SWITCH_PROJECT_TO_ONLINE_0 = "RPT_SWITCH_PROJECT_TO_ONLINE_0";

	public static final String RPT_SWITCH_PROJECT_TO_ORIGINAL_0 = "RPT_SWITCH_PROJECT_TO_ORIGINAL_0";

	public static final String RPT_STATUSCODE_ERROR_1 = "RPT_STATUSCODE_ERROR_1";
	
	/**
     * Hides the public constructor for this utility class.<p>
     */
    private Messages() {

        // hide the constructor
    }

    /**
     * Returns an instance of this localized message accessor.<p>
     * 
     * @return an instance of this localized message accessor
     */
    public static I_CmsMessageBundle get() {

        return INSTANCE;
    }

    /**
     * Returns the bundle name for this OpenCms package.<p>
     * 
     * @return the bundle name for this OpenCms package
     */
    public String getBundleName() {

        return BUNDLE_NAME;
    }

}