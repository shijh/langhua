/* This library is part of Taglib OpenCms module of Langhua
 *
 * Copyright (C) 2008 Langhua Opensource (http://www.langhua.org)
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.opencms.misc;

import org.opencms.i18n.A_CmsMessageBundle;
import org.opencms.i18n.I_CmsMessageBundle;

public final class Messages extends A_CmsMessageBundle {
    private static final String BUNDLE_NAME = Messages.class.getName() + ".messages_zh";  
    private static final I_CmsMessageBundle INSTANCE = new Messages();
    
    public static final String SE_HOURS="sehours";
    public static final String ES_HOURS="eshours";
    public static final String SB_HOURS="sbhours";
    public static final String S_DAYS="sdays";
    public static final String A_WEEK="aweek";
    public static final String MONTH="month";
    public static final String THREE_MONTHS="threemonths";
    public static final String SIX_MONTHS="sixmonths";
    public static final String YEAR="year";
    public static final String YEARS="years";
    public static final String THREE_YEARS="threeyears";
    public static final String UNLIMITED="unlimited";
    public static final String GREATER_THAN="greaterthan";
    public static final String LESS_THAN="lessthan";
    
    private Messages() {
    }

    public static I_CmsMessageBundle get() {

        return INSTANCE;
    }

    
    public String getBundleName() {

        return BUNDLE_NAME;
    }

}
