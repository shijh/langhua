/*
 * This library is part of OpenCms-SearchPipeline module of Langhua
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
 * For the latest version about this module, please see the
 * project website: http://langhua.org/portal/portal/default/OpenCms
 * 
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * For further information about Lucene, please see the
 * project website: http://lucene.apache.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.opencms.search.pipeline;

import org.opencms.i18n.A_CmsMessageBundle;
import org.opencms.i18n.I_CmsMessageBundle;

/**
 * Convenience class to access the localized messages of this module.<p> 
 * 
 * @author Wang Rui, wangrui@langhua.cn 
 * 
 */
public final class Messages extends A_CmsMessageBundle {
    
    /** Name of the used resource bundle. */
    private static final String BUNDLE_NAME = "org.langhua.opencms.search.pipeline.messages";

    /** Static instance member. */
    private static final I_CmsMessageBundle INSTANCE = new Messages();
    
    public static final String GUI_SEARCH_RESULT_0 = "GUI_SEARCH_RESULT_0";
    
    public static final String GUI_SEARCH_BUTTON_NAME_0 = "GUI_SEARCH_BUTTON_NAME_0";
    
    public static final String GUI_SEARCH_RESULT_NO_TITLE_0 = "GUI_SEARCH_RESULT_NO_TITLE_0";

    public static final String GUI_SEARCH_RESULT_TITLE_0 = "GUI_SEARCH_RESULT_TITLE_0";

    public static final String GUI_SEARCH_RESULT_5 = "GUI_SEARCH_RESULT_5";
    
    public static final String GUI_SEARCH_PAGE_NEXT_0 = "GUI_SEARCH_PAGE_NEXT_0";
    
    public static final String GUI_SEARCH_PAGE_PREVIOUSE_0 = "GUI_SEARCH_PAGE_PREVIOUSE_0";

    public static final String GUI_SEARCH_PAGE_JS_ALERT_0 = "GUI_SEARCH_PAGE_JS_ALERT_0";

    public static final String GUI_SEARCH_SORTED_0 = "GUI_SEARCH_SORTED_0";

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