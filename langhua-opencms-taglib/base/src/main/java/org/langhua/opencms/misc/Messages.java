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
