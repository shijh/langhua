/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.langhua.ofbiz.htmlreport.sample;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.commons.io.FileUtils;
import org.langhua.ofbiz.htmlreport.A_OFBizReportThread;
import org.langhua.ofbiz.htmlreport.I_OFBizReport;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.FileUtil;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.service.LocalDispatcher;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Thread for running sample import excel html report. <p>
 * 
 */
public class SampleParseExcelHtmlThread extends A_OFBizReportThread {
	
	public static final String module = SampleParseExcelHtmlThread.class.getName();

	public static final String SAMPLE_EXCEL = "sample_excel";
	
	public static final String CONFIRM = "confirm_action";
	
	public static final String[] m_messageLabels = new String[] {"FORMAT_DEFAULT", "FORMAT_WARNING", "FORMAT_HEADLINE", "FORMAT_NOTE", "FORMAT_OK", "FORMAT_ERROR", "FORMAT_THROWABLE"};
	
	public static final List<String> m_messages = Collections.unmodifiableList(Arrays.asList(m_messageLabels));
	
	public static final String[] m_columnNameLabels = new String[] {"TESTID", "Content", "User"};
	
	public static final List<String> m_columnNames = Collections.unmodifiableList(Arrays.asList(m_columnNameLabels));
	
	private LocalDispatcher m_dispatcher;
	
	private GenericDelegator m_delegator;
	
	public static final String m_encoding = "UTF-8";
	
	private static final String SAMPLE_EXCEL_XLS_FILENAME = "hot-deploy/htmlreport/testdata/sample_excel.xls";

	private static final String SAMPLE_EXCEL_XLSX_FILENAME = "hot-deploy/htmlreport/testdata/sample_excel.xlsx";

	/**
     * Constructor, creates a new html thread.<p>
	 * 
	 * @param request
	 * @param response
	 * @param name
	 */
    public SampleParseExcelHtmlThread(HttpServletRequest request, HttpServletResponse response, String name) {

        super(request, response, name);
        initHtmlReport(request, response);
		m_dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
		m_delegator = m_dispatcher.getDelegator();
    }

    /**
     * @see org.langhua.ofbiz.report.A_OFBizReportThread#getReportUpdate()
     */
    public String getReportUpdate() {

        return getReport().getReportUpdate();
    }

    /**
     * @see java.lang.Runnable#run()
     */
    public void run() {

        try {
            if (getName().startsWith(SAMPLE_EXCEL)) {
            	getReport().println(UtilProperties.getMessage("ReportUiLabels", "StartParseExcel", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
            	getReport().println("  Using " + m_encoding + " to parsing the excel file.", I_OFBizReport.FORMAT_DEFAULT);
            	parseSampleExcelXls();
            	parseSampleExcelXlsx();
            	getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelParseCompleted", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
        	} else {
            	getReport().println(getName(), I_OFBizReport.FORMAT_ERROR);
            	Debug.logError(getName(), module);
            }
        } catch (Exception e) {
        	getReport().println(e);
            if (Debug.errorOn()) {
                Debug.log(e);
            }
        }
    }

    /**
     * Parse sample excel file in xls format.
     * 
     */
	private void parseSampleExcelXls() {
		try {
			// 1. read the sample excel file
			File sampleExcelFile = FileUtil.getFile(SAMPLE_EXCEL_XLS_FILENAME);
			FileInputStream is = FileUtils.openInputStream(sampleExcelFile);
			
			// 2. use jexcel (jxl.jar) to load this bytes
        	getReport().println("  Using jexcel to parsing the xls format excel file...", I_OFBizReport.FORMAT_DEFAULT);
			WorkbookSettings ws = new WorkbookSettings();
			ws.setEncoding(m_encoding);
			Workbook workbook = Workbook.getWorkbook(is, ws);
			
			// 3. only first sheet will be parsed
			// 3.1 verify the file has a table at least
			int sheets = workbook.getSheets().length;
			if (sheets < 1) {
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableNoSheet", getLocale()), I_OFBizReport.FORMAT_ERROR);
				return;
			} else if (sheets > 1) {
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableOnlyParse1stSheet", getLocale()), I_OFBizReport.FORMAT_WARNING);
			}
			
			// 3.2 verify the first table has 2 rows at least
			Sheet sheet = workbook.getSheet(0);
			int rows = sheet.getRows();
			if (rows > 1) {
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableRows", new Object[] {String.valueOf(rows), sheet.getName()}, getLocale()), I_OFBizReport.FORMAT_NOTE);
			} else if (rows == 1) {
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableRow", new Object[] {sheet.getName()}, getLocale()), I_OFBizReport.FORMAT_ERROR);
				return;
			} else {
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableNoRow", new Object[] {sheet.getName()}, getLocale()), I_OFBizReport.FORMAT_ERROR);
				return;
			}
			
			// 3.3 verify the first row is just the same as sample file, if not, print error and return
			Cell[] cols = sheet.getRow(0);
			if (cols.length != m_columnNames.size()) {
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableColumnNumberNotMatch", new Object[] {String.valueOf(cols.length), String.valueOf(m_columnNames.size())}, getLocale()), I_OFBizReport.FORMAT_ERROR);
				return;
			}
			for (int i = 0; i < cols.length; i++) {
				String coltext = cols[i].getContents().trim();
				if (!coltext.equals(m_columnNames.get(i))) {
					getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableColumnNotMatch", new Object[] {String.valueOf(i + 1), coltext, m_columnNames.get(i)}, getLocale()), I_OFBizReport.FORMAT_ERROR);
					return;
				}
			}
			
	        // 4. parse row by row
			for (int i = 1; i < rows; i++) {
				// 4.1 get cells
				Cell[] cells = sheet.getRow(i);
				int length = cells.length;
				String testId = "";
				if (length > 0) testId = cells[0].getContents();
				String content = "";
				if (length > 1) content = cells[1].getContents();
				String user = "";
				if (length > 2) user= cells[2].getContents();
				getReport().print(UtilProperties.getMessage("ReportUiLabels", "ExcelParsingRow", new Object[] {String.valueOf(i+1), testId + "   " + content + "   " + user}, getLocale()), I_OFBizReport.FORMAT_DEFAULT);
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ok", getLocale()), I_OFBizReport.FORMAT_OK);
			}
		} catch (BiffException e) {
			getReport().println(e);
			Debug.logError(e, module);
		} catch (IOException e) {
			getReport().println(e);
			Debug.logError(e, module);
		}
	}

    /**
     * Parse sample excel file in xlsx format.
     * 
     */
	private void parseSampleExcelXlsx() {
		try {
			// 1. read the sample excel file
			File sampleExcelFile = FileUtil.getFile(SAMPLE_EXCEL_XLSX_FILENAME);
			FileInputStream is = FileUtils.openInputStream(sampleExcelFile);
			
			// 2. use POI to load this bytes
			getReport().println();
			getReport().println();
        	getReport().println("  Using Apache POI XSSF to parsing the xlsx format excel file...", I_OFBizReport.FORMAT_DEFAULT);
			XSSFWorkbook workbook = new XSSFWorkbook(is);
			
			// 3. only first sheet will be parsed
			// 3.1 verify the file has a table at least
			int sheets = workbook.getNumberOfSheets();
			if (sheets < 1) {
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableNoSheet", getLocale()), I_OFBizReport.FORMAT_ERROR);
				return;
			} else if (sheets > 1) {
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableOnlyParse1stSheet", getLocale()), I_OFBizReport.FORMAT_WARNING);
			}
			
			// 3.2 verify the first table has 2 rows at least
			XSSFSheet sheet = workbook.getSheetAt(0);
			int rows = sheet.getPhysicalNumberOfRows();
			if (rows > 1) {
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableRows", new Object[] {String.valueOf(rows), sheet.getSheetName()}, getLocale()), I_OFBizReport.FORMAT_NOTE);
			} else if (rows == 1) {
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableRow", new Object[] {sheet.getSheetName()}, getLocale()), I_OFBizReport.FORMAT_ERROR);
				return;
			} else {
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableNoRow", new Object[] {sheet.getSheetName()}, getLocale()), I_OFBizReport.FORMAT_ERROR);
				return;
			}
			
			// 3.3 verify the first row is just the same as sample file, if not, print error and return
			short cols = sheet.getRow(0).getLastCellNum();
			if (cols != m_columnNames.size()) {
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableColumnNumberNotMatch", new Object[] {String.valueOf(cols), String.valueOf(m_columnNames.size())}, getLocale()), I_OFBizReport.FORMAT_ERROR);
				return;
			}
			for (int i = 0; i < cols; i++) {
				String coltext = sheet.getRow(0).getCell(i).getStringCellValue().trim();
				if (!coltext.equals(m_columnNames.get(i))) {
					getReport().println(UtilProperties.getMessage("ReportUiLabels", "ExcelTableColumnNotMatch", new Object[] {String.valueOf(i + 1), coltext, m_columnNames.get(i)}, getLocale()), I_OFBizReport.FORMAT_ERROR);
					return;
				}
			}
			
	        // 4. parse row by row
			for (int i = 1; i < rows; i++) {
				// 4.1 get cells
				XSSFRow cells = sheet.getRow(i);
				int length = cells.getLastCellNum();
				String testId = "";
				if (length > 0) testId = cells.getCell(0).getStringCellValue();
				String content = "";
				if (length > 1) content = cells.getCell(1).getStringCellValue();
				String user = "";
				if (length > 2) user= cells.getCell(2).getStringCellValue();
				getReport().print(UtilProperties.getMessage("ReportUiLabels", "ExcelParsingRow", new Object[] {String.valueOf(i+1), testId + "   " + content + "   " + user}, getLocale()), I_OFBizReport.FORMAT_DEFAULT);
				getReport().println(UtilProperties.getMessage("ReportUiLabels", "ok", getLocale()), I_OFBizReport.FORMAT_OK);
			}
		} catch (IOException e) {
			getReport().println(e);
			Debug.logError(e, module);
		}
	}
	
	public XMLReader fetchSheetParser(SharedStringsTable sst) throws SAXException {
		XMLReader parser =
			XMLReaderFactory.createXMLReader(
					"org.apache.xerces.parsers.SAXParser"
			);
		ContentHandler handler = new SheetHandler(sst);
		parser.setContentHandler(handler);
		return parser;
	}
	
	/** 
	 * See org.xml.sax.helpers.DefaultHandler javadocs 
	 */
	private static class SheetHandler extends DefaultHandler {
		private SharedStringsTable sst;
		private String lastContents;
		private boolean nextIsString;
		
		private SheetHandler(SharedStringsTable sst) {
			this.sst = sst;
		}
		
		public void startElement(String uri, String localName, String name,
				Attributes attributes) throws SAXException {
			// c => cell
			if(name.equals("c")) {
				// Print the cell reference
				System.out.print(attributes.getValue("r") + " - ");
				// Figure out if the value is an index in the SST
				String cellType = attributes.getValue("t");
				if(cellType != null && cellType.equals("s")) {
					nextIsString = true;
				} else {
					nextIsString = false;
				}
			}
			// Clear contents cache
			lastContents = "";
		}
		
		public void endElement(String uri, String localName, String name)
				throws SAXException {
			// Process the last contents as required.
			// Do now, as characters() may be called more than once
			if(nextIsString) {
				int idx = Integer.parseInt(lastContents);
				lastContents = new XSSFRichTextString(sst.getEntryAt(idx)).toString();
			}

			// v => contents of a cell
			// Output after we've seen the string contents
			if(name.equals("v")) {
				System.out.println(lastContents);
			}
		}

		public void characters(char[] ch, int start, int length)
				throws SAXException {
			lastContents += new String(ch, start, length);
		}
	}
}
