/* This library is part of Taglib OpenCms module of Langhua
 *
 * Copyright (C) 2007 Langhua Opensource (http://www.langhua.org)
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
package org.langhua.opencms.taglib.datetime;

import org.opencms.i18n.A_CmsMessageBundle;
import org.opencms.i18n.I_CmsMessageBundle;

/**
 * Convenience class to access the localized messages of this OpenCms package.<p> 
 * 
 */
public final class Messages extends A_CmsMessageBundle {
	
    private static final String BUNDLE_NAME = "cn.langhua.opencms.taglib.datetime.messages_zh";
    
    private static final I_CmsMessageBundle INSTANCE = new Messages();
 
    public static final String YEAR="label_year";
    public static final String MONTH="label_month";
    public static final String DAY="label_day";
    public static final String LUNAR_CALENDER="label_lunar_calendar";
    public static final String ZERO="label_zero";
    public static final String ONE="label_one";
    public static final String TWO="label_two";
    public static final String THREE="label_three";
    public static final String FOUR="label_four";
    public static final String FIVE="label_five";
    public static final String SIX="label_six";
    public static final String SEVEN="label_seven";
    public static final String EIGHT="label_eight";
    public static final String NINE="label_nine";
    public static final String TEN="label_ten";
    public static final String WINTER="label_winter";
    public static final String LAY="label_lay";
    public static final String EMBOLISM="label_embolism";
    public static final String KALENDS="label_kalends";
    public static final String CHUER="label_chuer";
    public static final String label_chusan="label_chusan";
    public static final String label_chusi="label_chusi";
    public static final String label_chuwu="label_chuwu";
    public static final String label_chuliu="label_chuliu";
    public static final String label_chuqi="label_chuqi";
    public static final String label_chuba="label_chuba";
    public static final String label_chujiu="label_chujiu";
    public static final String label_chushi="label_chushi";
    public static final String label_shiyi="label_shiyi";
    public static final String label_shier="label_shier";
    public static final String label_shisan="label_shisan";
    public static final String label_shisi="label_shisi";
    public static final String label_shiwu="label_shiwu";
    public static final String label_shiliu="label_shiliu";
    public static final String label_shiqi="label_shiqi";
    public static final String label_shiba="label_shiba";
    public static final String label_shijiu="label_shijiu";
    public static final String label_ershi="label_ershi";
    public static final String label_eryi="label_eryi";
    public static final String label_erer="label_erer";
    public static final String label_ersan="label_ersan";
    public static final String label_ersi="label_ersi";
    public static final String label_erwu="label_erwu";
    public static final String label_erliu="label_erliu";
    public static final String label_erqi="label_erqi";
    public static final String label_erba="label_erba";
    public static final String label_erjiu="label_erjiu";
    public static final String label_sanshi="label_sanshi";
    public static final String label_zeroy="label_zeroy";
    public static final String label_pleasejsj="label_pleasejsj";
    public static final String label_jia="label_jia";
    public static final String label_yi="label_yi";
    public static final String label_bing="label_bing";
    public static final String label_ding="label_ding";
    public static final String label_wu="label_wu";
    public static final String label_yit="label_yit";
    public static final String label_geng="label_geng";
    public static final String label_xin="label_xin";
    public static final String label_ren="label_ren";
    public static final String label_kui="label_kui";
    public static final String label_zi="label_zi";
    public static final String label_chou="label_chou";
    public static final String label_yan="label_yan";
    public static final String label_mou="label_mou";
    public static final String label_chen="label_chen";
    public static final String label_si="label_si";
    public static final String label_wut="label_wut";
    public static final String label_wei="label_wei";
    public static final String label_shen="label_shen";
    public static final String label_you="label_you";
    public static final String label_xu="label_xu";
    public static final String label_hai="label_hai";

    public static final String XQYI="label_xqyi";
    public static final String XQER="label_xqer";
    public static final String XQSAN="label_xqsan";
    public static final String XQSI="label_xqsi";
    public static final String XQWU="label_xqwu";
    public static final String XQLIU="label_xqliu";
    public static final String XQRI="label_xqri";
	
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
