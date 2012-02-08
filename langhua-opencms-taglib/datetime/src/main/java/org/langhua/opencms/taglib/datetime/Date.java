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
package org.langhua.opencms.taglib.datetime;

import org.apache.commons.logging.Log;
import org.opencms.main.*;
import org.opencms.util.CmsStringUtil;
import org.opencms.jsp.*;
import org.opencms.file.*;

import org.langhua.opencms.taglib.commons.A_LanghuaTag;

import javax.servlet.http.*;
import javax.servlet.jsp.*;


/**
* example: <br>
* 
* &lt;%@ taglib &nbsp prefix="lh-dt" &nbsp uri="http://www.langhua.org/taglib/datetime"%&gt;<br>
* 
* &lt;lh-dt:date &nbsp cssfile="/taglib/CssSrc" /&gt;<br>
*  
* cssfile : css file.<br>
* 
* Image name: Date-display.PNG<br>
* 
*/
public class Date extends A_LanghuaTag {

	private static final long serialVersionUID = 1L;

	private static final Log LOG = CmsLog.getLog(Date.class);

	CmsObject cmso;

	public int doStartTag() throws JspTagException {

		String htmlbody = buildHtml();
		try {
			pageContext.getOut().print(htmlbody);
		} catch (Exception e) {

			 if (LOG.isDebugEnabled()) {
	                LOG.debug(e);
	         }
		}
		return SKIP_BODY;
	}

	public String buildHtml() {
		StringBuffer html = new StringBuffer(512);		
		HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
		HttpServletResponse response = (HttpServletResponse) pageContext.getResponse();
		CmsJspActionElement cms = new CmsJspActionElement(pageContext, request,	response);	
		try {
			String cssfile = cms.getRequestContext().removeSiteRoot(getCssFile());

			cmso = cms.getCmsObject();
			CmsFile configFile = cmso.readFile(cssfile, CmsResourceFilter.IGNORE_EXPIRATION);
			String fileName = configFile.getName();
			if (CmsStringUtil.isEmpty(getCssIndicator())) {
				html.append("<style type=\"text/css\">\n");
				html.append("<!--");
				html.append(buildCSS(cms, cssfile));
				html.append("-->");
				html.append("</style>\n");
			}
			if (CmsStringUtil.isNotEmpty(getCssIndicator()) && A_LanghuaTag.CSS_INDICATOR_NOSTYLE.equals(getCssIndicator())) {			
				html.append(buildCSS(cms, cssfile));
		    }
			if (CmsStringUtil.isEmpty(getCssIndicator()) || A_LanghuaTag.CSS_INDICATOR_CUSTOMIZED.equals(getCssIndicator())) {
				html.append("<div class=\"topDays"+ fileName+ "\">\n");
				html.append("<SCRIPT language=JavaScript>\n");
				html.append("function RunGLNL(){\n");
				html.append("var today=new Date();\n");
				html.append("var DDDD=(today.getFullYear())+\""+Messages.YEAR+"\"+(today.getMonth()+1)+\""+Messages.MONTH+"\"+today.getDate()+\""+Messages.DAY+"\";\n");
                html.append("DDDD = DDDD+ \" \"+\""+Messages.LUNAR_CALENDER+"\"+\" \" + (CnDateofDateStr(today));\n");                               
                html.append("document.write(DDDD);\n");
                html.append("}\n");
                html.append("function DaysNumberofDate(DateGL){;\n");
                html.append("return parseInt((Date.parse(DateGL)-Date.parse(DateGL.getFullYear()+\"/1/1\"))/86400000)+1;\n");
                html.append("}\n");
                html.append("function CnDateofDate(DateGL){\n");
                html.append("var CnData=new Array(\n");
                html.append("0x16,0x2a,0xda,0x00,0x83,0x49,0xb6,0x05,0x0e,0x64,0xbb,0x00,0x19,0xb2,0x5b,0x00,\n");
                html.append("0x87,0x6a,0x57,0x04,0x12,0x75,0x2b,0x00,0x1d,0xb6,0x95,0x00,0x8a,0xad,0x55,0x02,\n");
                html.append("0x15,0x55,0xaa,0x00,0x82,0x55,0x6c,0x07,0x0d,0xc9,0x76,0x00,0x17,0x64,0xb7,0x00,\n");
                html.append("0x86,0xe4,0xae,0x05,0x11,0xea,0x56,0x00,0x1b,0x6d,0x2a,0x00,0x88,0x5a,0xaa,0x04,\n");
                html.append("0x14,0xad,0x55,0x00,0x81,0xaa,0xd5,0x09,0x0b,0x52,0xea,0x00,0x16,0xa9,0x6d,0x00,\n");
                html.append("0x84,0xa9,0x5d,0x06,0x0f,0xd4,0xae,0x00,0x1a,0xea,0x4d,0x00,0x87,0xba,0x55,0x04 ); \n");
                html.append("var CnMonth=new Array();\n");
                html.append("var CnMonthDays=new Array();\n");
                html.append("var CnBeginDay;\n");
                html.append("var LeapMonth;\n");
                html.append("var Bytes=new Array();\n");
                html.append("var I;\n");
                html.append("var CnMonthData;\n");
                html.append("var DaysCount;\n");
                html.append("var CnDaysCount;\n");
                html.append("var ResultMonth;\n");
                html.append("var ResultDay;\n");
                html.append("var yyyy=DateGL.getFullYear();\n");
                html.append("var mm=DateGL.getMonth()+1;\n");
            	html.append("var dd=DateGL.getDate();\n");
                html.append("if(yyyy<100) yyyy+=1900;\n");
            	html.append("if ((yyyy < 1997) || (yyyy > 2020)){\n");
            	html.append("return 0;\n");
            	html.append("    }\n");
            	html.append(" Bytes[0] = CnData[(yyyy - 1997) * 4];\n");
                html.append("  Bytes[1] = CnData[(yyyy - 1997) * 4 + 1];\n");
                html.append("  Bytes[2] = CnData[(yyyy - 1997) * 4 + 2];\n");
                html.append("  Bytes[3] = CnData[(yyyy - 1997) * 4 + 3];\n");
                html.append("  if ((Bytes[0] & 0x80) != 0) {CnMonth[0] = 12;}\n");
                html.append("  else {CnMonth[0] = 11;}\n");
                html.append("  CnBeginDay = (Bytes[0] & 0x7f);\n");
                html.append("  CnMonthData = Bytes[1];\n");
                html.append("  CnMonthData = CnMonthData << 8;\n");
                html.append("  CnMonthData = CnMonthData | Bytes[2];\n");
                html.append("  LeapMonth = Bytes[3];\n");
                html.append(" for (I=15;I>=0;I--){\n");
                html.append("  CnMonthDays[15 - I] = 29;\n");
                html.append(" if (((1 << I) & CnMonthData) != 0 ){\n");
                html.append(" CnMonthDays[15 - I]++;}\n");
                html.append("if (CnMonth[15 - I] == LeapMonth ){\n");
                html.append("CnMonth[15 - I + 1] = - LeapMonth;}\n");
                html.append(" else{\n");
                html.append(" if (CnMonth[15 - I] < 0 ){CnMonth[15 - I + 1] = - CnMonth[15 - I] + 1;}\n");
                html.append("else {CnMonth[15 - I + 1] = CnMonth[15 - I] + 1;}\n");
                html.append("if (CnMonth[15 - I + 1] > 12 ){ CnMonth[15 - I + 1] = 1;}\n");
                html.append("    }\n");
                html.append(" }\n");
                html.append("  DaysCount = DaysNumberofDate(DateGL) - 1;\n");
                html.append("  if (DaysCount <= (CnMonthDays[0] - CnBeginDay)){\n");
                html.append("  if ((yyyy > 1901) && (CnDateofDate(new Date((yyyy - 1)+\"/12/31\")) < 0)){\n");
                html.append("  ResultMonth = - CnMonth[0];}\n");
                html.append("else {ResultMonth = CnMonth[0];}\n");
                html.append("ResultDay = CnBeginDay + DaysCount;\n");
                html.append("}\n");
                html.append("else{\n");
                html.append("CnDaysCount = CnMonthDays[0] - CnBeginDay;\n");
                html.append("I = 1;\n");
                html.append("while ((CnDaysCount < DaysCount) && (CnDaysCount + CnMonthDays[I] < DaysCount)){\n");
                html.append("CnDaysCount+= CnMonthDays[I];\n");
                html.append("I++;\n");
                html.append("}\n");
                html.append("ResultMonth = CnMonth[I];\n");
                html.append("ResultDay = DaysCount - CnDaysCount;\n");
                html.append("}\n");
                html.append("if (ResultMonth > 0){\n");
                html.append("return ResultMonth * 100 + ResultDay;}\n");
                html.append("else{return ResultMonth * 100 - ResultDay;}\n");
                html.append("}\n");
                html.append("function CnYearofDate(DateGL){\n");
                html.append("var YYYY=DateGL.getFullYear();\n");
                html.append("var MM=DateGL.getMonth()+1;\n");
                html.append("var CnMM=parseInt(Math.abs(CnDateofDate(DateGL))/100);\n");
                html.append("if(YYYY<100) YYYY+=1900;\n");
                html.append("if(CnMM>MM) YYYY--;\n");
                html.append("YYYY-=1864;\n");
                html.append("return CnEra(YYYY)+\""+Messages.YEAR+"\";\n");
                html.append("}\n");
                html.append("function CnMonthofDate(DateGL){\n");
                html.append("var  CnMonthStr=new Array(\""+Messages.ZERO+"\",\""+Messages.ONE+"\",\""+Messages.TWO+"\",\""+Messages.THREE+"\",\""+Messages.FOUR+"\",\""+Messages.FIVE+"\"" +
                		",\""+Messages.SIX+"\",\""+Messages.SEVEN+"\",\""+Messages.EIGHT+"\",\""+Messages.NINE+"\",\""+Messages.TEN+"\",\""+Messages.WINTER+"\",\""+Messages.LAY+"\");\n");
                html.append("var  Month;\n");
                html.append("Month = parseInt(CnDateofDate(DateGL)/100);\n");
                html.append("if (Month < 0){return \""+Messages.EMBOLISM+"\" + CnMonthStr[-Month] + \""+Messages.MONTH+"\";}\n");
                html.append("else{return CnMonthStr[Month] +\""+Messages.MONTH+"\";}\n");
                html.append("} \n");
                html.append("function CnDayofDate(DateGL){\n");
                html.append("var CnDayStr=new Array(\""+Messages.ZERO+"\",\n");
                html.append("\""+Messages.KALENDS+"\", \""+Messages.CHUER+"\",\""+Messages.label_chusan+"\", \""+Messages.label_chusi+"\", \""+Messages.label_chuwu+"\",\n");
                html.append("\""+Messages.label_chuliu+"\", \""+Messages.label_chuqi+"\",\""+Messages.label_chuba+"\", \""+Messages.label_chujiu+"\", \""+Messages.label_chushi+"\",\n");
                html.append("\""+Messages.label_shiyi+"\", \""+Messages.label_shier+"\",\""+Messages.label_shisan+"\", \""+Messages.label_shisi+"\", \""+Messages.label_shiwu+"\",\n");
                html.append("\""+Messages.label_shiliu+"\", \""+Messages.label_shiqi+"\", \""+Messages.label_shiba+"\", \""+Messages.label_shijiu+"\", \""+Messages.label_ershi+"\",\n");
                html.append("\""+Messages.label_eryi+"\", \""+Messages.label_erer+"\", \""+Messages.label_ersan+"\", \""+Messages.label_ersi+"\", \""+Messages.label_erwu+"\",\n");
                html.append("\""+Messages.label_erliu+"\", \""+Messages.label_erqi+"\", \""+Messages.label_erba+"\", \""+Messages.label_erjiu+"\", \""+Messages.label_sanshi+"\");\n");
                html.append("var Day;\n");
                html.append("Day = (Math.abs(CnDateofDate(DateGL)))%100;\n");
                html.append("return CnDayStr[Day];\n");
                html.append("}\n");
                html.append("function CnEra(YYYY){\n");
                html.append("var Tiangan=new Array(\""+Messages.label_jia+"\",\""+Messages.label_yi+"\",\""+Messages.label_bing+"\",\""+Messages.label_ding+"\",\""+Messages.label_wu+"\",\""+Messages.label_yit+"\",\""+Messages.label_geng+"\",\""+Messages.label_xin+"\",\""+Messages.label_ren+"\",\""+Messages.label_kui+"\");\n");
                html.append("var Dizhi=new Array(\""+Messages.label_zi+"\",\""+Messages.label_chou+"\",\""+Messages.label_yan+"\",\""+Messages.label_mou+"\",\""+Messages.label_chen+"\",\""+Messages.label_si+"\",\""+Messages.label_wut+"\",\""+Messages.label_wei+"\",\""+Messages.label_shen+"\",\""+Messages.label_you+"\",\""+Messages.label_xu+"\",\""+Messages.label_hai+"\");\n");
                html.append("return Tiangan[YYYY%10]+Dizhi[YYYY%12];\n");
                html.append("} \n");
                html.append("function CnDateofDateStr(DateGL){\n");
                html.append("if(CnMonthofDate(DateGL)==\""+Messages.label_zeroy+"\") return \""+Messages.label_pleasejsj+"\";\n");
                html.append("else return CnYearofDate(DateGL)+ CnMonthofDate(DateGL) + CnDayofDate(DateGL);\n");
                html.append("}\n");
                html.append("RunGLNL();\n");
                html.append("</SCRIPT>\n");
                html.append("</div>\n");
			}	

		} catch (Exception e1) {
			 if (LOG.isDebugEnabled()) {
	                LOG.debug(e1);
	         }
		}

		return html.toString();
	}
}