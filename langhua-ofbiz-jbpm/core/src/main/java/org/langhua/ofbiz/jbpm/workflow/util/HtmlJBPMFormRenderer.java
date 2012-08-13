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

package org.langhua.ofbiz.jbpm.workflow.util;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javolution.util.FastMap;

import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.MatchResult;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.jbpm.graph.def.Transition;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilFormatOut;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericEntity;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.model.ModelEntity;
import org.ofbiz.entity.model.ModelField;
import org.ofbiz.entity.model.ModelReader;
import org.ofbiz.widget.form.ModelForm;
import org.ofbiz.widget.form.ModelFormField;
import org.ofbiz.widget.form.ModelFormField.CheckField;
import org.ofbiz.widget.form.ModelFormField.DateTimeField;
import org.ofbiz.widget.form.ModelFormField.DisplayEntityField;
import org.ofbiz.widget.form.ModelFormField.DisplayField;
import org.ofbiz.widget.form.ModelFormField.DropDownField;
import org.ofbiz.widget.form.ModelFormField.HiddenField;
import org.ofbiz.widget.form.ModelFormField.OptionValue;
import org.ofbiz.widget.form.ModelFormField.RadioField;
import org.ofbiz.widget.form.ModelFormField.TextField;
import org.ofbiz.widget.form.ModelFormField.TextareaField;
import org.ofbiz.widget.html.HtmlFormRenderer;

import org.langhua.ofbiz.jbpm.I_OFBizTaskInstance;


/**
 * Widget Library - HTML Form Renderer implementation
 */
public class HtmlJBPMFormRenderer extends HtmlFormRenderer {

    public static final String module = HtmlJBPMFormRenderer.class.getName();
    
    HttpServletRequest request;
    HttpServletResponse response;

    protected HtmlJBPMFormRenderer() {}

    public HtmlJBPMFormRenderer(HttpServletRequest request, HttpServletResponse response) {
    	super(request, response);
    	this.request = request;
    	this.response = response;
    }

    /* (non-Javadoc)
     * @see org.ofbiz.widget.form.FormStringRenderer#renderDisplayField(java.lang.Appendable, java.util.Map, org.ofbiz.widget.form.ModelFormField.DisplayField)
     */
    public void renderDisplayField(Appendable writer, Map<String, Object> context, DisplayField displayField) throws IOException {
        ModelFormField modelFormField = displayField.getModelFormField();

        StringBuffer str = new StringBuffer();

		displayField.setDescription(String.valueOf(getEntityFieldValueForJBPM(context, modelFormField)));

        if (UtilValidate.isNotEmpty(modelFormField.getWidgetStyle()) || modelFormField.shouldBeRed(context)) {
            str.append("<span class=\"");
            str.append(modelFormField.getWidgetStyle());
            // add a style of red if this is a date/time field and redWhen is true
            if (modelFormField.shouldBeRed(context)) {
                str.append(" alert");
            }
            str.append("\">");
        }

        if (str.length() > 0) {
            writer.append(str);
        }
        String description = displayField.getDescription(context);
        //Replace new lines with <br>
        description = description.replaceAll("\n", "<br>");
        writer.append(description);
        if (str.length() > 0) {
        	writer.append("</span>");
        }
        
        if (displayField instanceof DisplayEntityField) {
			this.makeHyperlinkString(writer, ((DisplayEntityField) displayField).getSubHyperlink(), context);
        }
        
		this.appendTooltip(writer, context, modelFormField);

        //this.appendWhitespace(buffer);
    }

    private Object getEntityFieldValueForJBPM(Map<String, Object> context, ModelFormField modelFormField) {
    	// check if it's a JBPM field
		Perl5Compiler compiler = new Perl5Compiler();
		Perl5Matcher matcher = new Perl5Matcher();
    	try {
			if (matcher.contains(modelFormField.getName(), compiler.compile(I_OFBizTaskInstance.ENTITY_FORM_REGEX))) {
				MatchResult result = matcher.getMatch();
				if (result.groups() == 3) {
					// Get entity name and field name
					String entityName = result.group(1).trim();
					String fieldName = result.group(2).trim();
					GenericDelegator delegator = modelFormField.getModelForm().getDelegator(context);
					ModelReader reader = delegator.getModelReader();
					try {
						// Get pks (ModelField objects)
						ModelEntity modelEntity = reader.getModelEntity(entityName);
						List<ModelField> pks = modelEntity.getPkFieldsUnmodifiable();
						
						// Fill pks from parameters
			        	Map<String, Object> parameters = (Map) context.get("parameters");
			        	Map<String, Object> pksFound = FastMap.newInstance();
			        	for (int i=0; i<pks.size(); i++) {
			        		ModelField pk = (ModelField) pks.get(i);
			        		Object parameter = parameters.get(pk.getName());
			        		if (parameter != null) {
			        			pksFound.put(pk.getName(), parameter);
			        		}
			        	}
						
						// TODO: Fill pks from request attributes
						
						// TODO: Fill pks from session attributes
						
						// Get entity instance
			        	if (!pksFound.isEmpty()) {
				        	List<GenericValue> values = delegator.findByAnd(entityName, pksFound);
				        	if (values != null && values.size() > 0) {
				        		GenericEntity entity = (GenericEntity) values.get(0);
								// Get the value of current display field in entity instance and display the value
								return entity.get(fieldName);
				        	}
			        	}
					} catch (GenericEntityException e) {
						Debug.logError(e, module);
					}
				}
			}
		} catch (MalformedPatternException e) {
			Debug.logError(e, module);
		}
		return "";
	}

    /* (non-Javadoc)
     * @see org.ofbiz.widget.form.FormStringRenderer#renderTextField(java.io.Writer, java.util.Map, org.ofbiz.widget.form.ModelFormField.TextField)
     */
    public void renderTextField(Appendable writer, Map<String, Object> context, TextField textField) throws IOException {
        ModelFormField modelFormField = textField.getModelFormField();

        writer.append("<input type=\"text\"");

        appendClassNames(writer, context, modelFormField);

        writer.append(" name=\"");
        writer.append(modelFormField.getParameterName(context));
        writer.append('"');

        String value = modelFormField.getEntry(context, textField.getDefaultValue(context));
        if (UtilValidate.isEmpty(value)) {
        	value = String.valueOf(getEntityFieldValueForJBPM(context, modelFormField));
        }
        if (UtilValidate.isNotEmpty(value)) {
        	writer.append(" value=\"");
        	writer.append(UtilFormatOut.encodeXmlValue(value));
        	writer.append('"');
        }

        writer.append(" size=\"");
        writer.append(String.valueOf(textField.getSize()));
        writer.append('"');

        Integer maxlength = textField.getMaxlength();
        if (maxlength != null) {
        	writer.append(" maxlength=\"");
        	writer.append(String.valueOf(maxlength.intValue()));
        	writer.append('"');
        }

        String idName = modelFormField.getIdName();
        if (UtilValidate.isNotEmpty(idName)) {
        	writer.append(" id=\"");
        	writer.append(idName);
        	writer.append('"');
        }

        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);
        if (UtilValidate.isNotEmpty(event) && UtilValidate.isNotEmpty(action)) {
        	writer.append(" ");
        	writer.append(event);
        	writer.append("=\"");
        	writer.append(action);
        	writer.append('"');
        }

        writer.append("/>");
        
        this.addAsterisks(writer, context, modelFormField);

        this.makeHyperlinkString(writer, textField.getSubHyperlink(), context);

        this.appendTooltip(writer, context, modelFormField);
    }

    /* (non-Javadoc)
     * @see org.ofbiz.widget.form.FormStringRenderer#renderTextareaField(java.io.Writer, java.util.Map, org.ofbiz.widget.form.ModelFormField.TextareaField)
     */
    public void renderTextareaField(Appendable writer, Map<String, Object> context, TextareaField textareaField) throws IOException {
        ModelFormField modelFormField = textareaField.getModelFormField();

        writer.append("<textarea");

        appendClassNames(writer, context, modelFormField);

        writer.append(" name=\"");
        writer.append(modelFormField.getParameterName(context));
        writer.append('"');

        writer.append(" cols=\"");
        writer.append(String.valueOf(textareaField.getCols()));
        writer.append('"');

        writer.append(" rows=\"");
        writer.append(String.valueOf(textareaField.getRows()));
        writer.append('"');

        String idName = modelFormField.getIdName();
        if (UtilValidate.isNotEmpty(idName)) {
        	writer.append(" id=\"");
        	writer.append(idName);
        	writer.append('"');
        } else if (textareaField.getVisualEditorEnable()) {
        	writer.append(" id=\"");
        	writer.append("htmlEditArea");
        	writer.append('"');
        }
 
        writer.append('>');

        String value = modelFormField.getEntry(context, textareaField.getDefaultValue(context));
        if (UtilValidate.isEmpty(value)) {
        	value = String.valueOf(getEntityFieldValueForJBPM(context, modelFormField));
        }
        if (UtilValidate.isNotEmpty(value)) {
        	writer.append(UtilFormatOut.encodeXmlValue(value));
        }

        writer.append("</textarea>");
        
        if (textareaField.getVisualEditorEnable()) {
        	writer.append("<script language=\"javascript\" src=\"/images/htmledit/whizzywig.js\" type=\"text/javascript\"></script>");
        	writer.append("<script language=\"javascript\" type=\"text/javascript\"> buttonPath = \"/images/htmledit/\"; cssFile=\"/images/htmledit/simple.css\";makeWhizzyWig(\"");
            if (UtilValidate.isNotEmpty(idName)) { 
            	writer.append(idName);
            } else {
            	writer.append("htmlEditArea");
            }
            writer.append("\",\"");
            String buttons = textareaField.getVisualEditorButtons(context);
            if (UtilValidate.isNotEmpty(buttons)) {
            	writer.append(buttons);
            } else {
            	writer.append("all");
            }
            writer.append("\") </script>");
        }

        this.addAsterisks(writer, context, modelFormField);

        this.appendTooltip(writer, context, modelFormField);

        //this.appendWhitespace(buffer);
    }

    /* (non-Javadoc)
     * @see org.ofbiz.widget.form.FormStringRenderer#renderDateTimeField(java.io.Writer, java.util.Map, org.ofbiz.widget.form.ModelFormField.DateTimeField)
     */
    public void renderDateTimeField(Appendable writer, Map<String, Object> context, DateTimeField dateTimeField) throws IOException {
        ModelFormField modelFormField = dateTimeField.getModelFormField();
        String paramName = modelFormField.getParameterName(context);
        String defaultDateTimeString = dateTimeField.getDefaultDateTimeString(context);
        
        Map<String, String> uiLabelMap = UtilGenerics.checkMap(context.get("uiLabelMap"));
        if (uiLabelMap == null) {
            Debug.logWarning("Could not find uiLabelMap in context", module);
        }
        String localizedInputTitle = "" , localizedIconTitle = "";

        // whether the date field is short form, yyyy-mm-dd
        boolean shortDateInput = ("date".equals(dateTimeField.getType()) || "time-dropdown".equals(dateTimeField.getInputMethod()) ? true : false);

        writer.append("<input type=\"text\"");

		appendClassNames(writer, context, modelFormField);

		writer.append(" name=\"");
        if ("time-dropdown".equals(dateTimeField.getInputMethod())) {
        	writer.append(UtilHttp.makeCompositeParam(paramName, "date"));
        } else {
        	writer.append(paramName);
        }
        writer.append('"');

        // the default values for a timestamp
        int size = 25;
        int maxlength = 30;

        if (shortDateInput) {
            size = maxlength = 10;
            if (uiLabelMap != null) {
                localizedInputTitle = (String) uiLabelMap.get("CommonFormatDate");
            }
        } else if ("time".equals(dateTimeField.getType())) {
            size = maxlength = 8;
            if (uiLabelMap != null) {
                localizedInputTitle = (String) uiLabelMap.get("CommonFormatTime");
            }
        } else {
            if (uiLabelMap != null) {
                localizedInputTitle = (String) uiLabelMap.get("CommonFormatDateTime");
            }
        }
        writer.append(" title=\"");
        writer.append(localizedInputTitle);
        writer.append('"');
        
        String value = modelFormField.getEntry(context, dateTimeField.getDefaultValue(context));
        if (UtilValidate.isEmpty(value)) {
        	value = String.valueOf(getEntityFieldValueForJBPM(context, modelFormField));
        }
        if (UtilValidate.isNotEmpty(value)) {
            if(value.length() > maxlength) {
                value = value.substring(0, maxlength);
            }
            writer.append(" value=\"");
            writer.append(value);
            writer.append('"');
        }
        
        writer.append(" size=\"");
        writer.append(String.valueOf(size));
        writer.append('"');

        writer.append(" maxlength=\"");
        writer.append(String.valueOf(maxlength));
        writer.append('"');

        String idName = modelFormField.getIdName();
        if (UtilValidate.isNotEmpty(idName)) {
        	writer.append(" id=\"");
        	writer.append(idName);
        	writer.append('"');
        }

        writer.append("/>");

        // search for a localized label for the icon
        if (uiLabelMap != null) {
            localizedIconTitle = (String) uiLabelMap.get("CommonViewCalendar");
        }

        // add calendar pop-up button and seed data IF this is not a "time" type date-time
        if (!"time".equals(dateTimeField.getType())) {
            if (shortDateInput) {
            	writer.append("<a href=\"javascript:call_cal_notime(document.");
            } else {
            	writer.append("<a href=\"javascript:call_cal(document.");
            }
            writer.append(modelFormField.getModelForm().getCurrentFormName(context));
            writer.append('.');
            if ("time-dropdown".equals(dateTimeField.getInputMethod())) {
            	writer.append(UtilHttp.makeCompositeParam(paramName, "date"));
            } else {
            	writer.append(paramName);
            }
            writer.append(",'");
            writer.append(UtilHttp.encodeBlanks(modelFormField.getEntry(context, defaultDateTimeString)));
            writer.append("');\">");
           
            writer.append("<img src=\"");
			this.appendContentUrl(writer, "/images/cal.gif");
			writer.append("\" width=\"16\" height=\"16\" border=\"0\" alt=\"");
			writer.append(localizedIconTitle);
			writer.append("\" title=\"");
			writer.append(localizedIconTitle);
			writer.append("\"/></a>");
        }
        
        // if we have an input method of time-dropdown, then render two dropdowns
        if ("time-dropdown".equals(dateTimeField.getInputMethod())) {       		
            String className = modelFormField.getWidgetStyle();
            String classString = (className != null ? " class=\"" + className + "\" " : "");
            boolean isTwelveHour = "12".equals(dateTimeField.getClock());

            // set the Calendar to the default time of the form or now()
            Calendar cal = null;
            try {
                Timestamp defaultTimestamp = Timestamp.valueOf(modelFormField.getEntry(context, defaultDateTimeString));
                cal = Calendar.getInstance();
                cal.setTime(defaultTimestamp);
            } catch (IllegalArgumentException e) {
                Debug.logWarning("Form widget field [" + paramName + "] with input-method=\"time-dropdown\" was not able to understand the default time [" 
                        + defaultDateTimeString + "]. The parsing error was: " + e.getMessage(), module);
            }

            // write the select for hours
            writer.append("&nbsp;<select name=\"").append(UtilHttp.makeCompositeParam(paramName, "hour")).append("\"");
            writer.append(classString).append(">");

            // keep the two cases separate because it's hard to understand a combined loop
            if (isTwelveHour) {
                for (int i = 1; i <= 12; i++) {
                	writer.append("<option value=\"").append(String.valueOf(i)).append("\"");
                    if (cal != null) { 
                        int hour = cal.get(Calendar.HOUR_OF_DAY);
                        if (hour == 0) hour = 12;
                        if (hour > 12) hour -= 12;
                        if (i == hour) writer.append(" selected");
                    }
                    writer.append(">").append(String.valueOf(i)).append("</option>");
                }
            } else {
                for (int i = 0; i < 24; i++) {
                	writer.append("<option value=\"").append(String.valueOf(i)).append("\"");
                    if (cal != null && i == cal.get(Calendar.HOUR_OF_DAY)) {
                    	writer.append(" selected");
                    }
                    writer.append(">").append(String.valueOf(i)).append("</option>");
                }
            }
            
            // write the select for minutes
            writer.append("</select>:<select name=\"");
            writer.append(UtilHttp.makeCompositeParam(paramName, "minutes")).append("\"");
            writer.append(classString).append(">");
            for (int i = 0; i < 60; i++) {
            	writer.append("<option value=\"").append(String.valueOf(i)).append("\"");
                if (cal != null && i == cal.get(Calendar.MINUTE)) {
                	writer.append(" selected");
                }
                writer.append(">").append(String.valueOf(i)).append("</option>");
            }
            writer.append("</select>");

            // if 12 hour clock, write the AM/PM selector
            if (isTwelveHour) {
                String amSelected = ((cal != null && cal.get(Calendar.AM_PM) == Calendar.AM) ? "selected" : "");
                String pmSelected = ((cal != null && cal.get(Calendar.AM_PM) == Calendar.PM) ? "selected" : "");
                writer.append("<select name=\"").append(UtilHttp.makeCompositeParam(paramName, "ampm")).append("\"");
                writer.append(classString).append(">");
                writer.append("<option value=\"").append("AM").append("\" ").append(amSelected).append(">AM</option>");
                writer.append("<option value=\"").append("PM").append("\" ").append(pmSelected).append(">PM</option>");
                writer.append("</select>");
            }

            // create a hidden field for the composite type, which is a Timestamp
            writer.append("<input type=\"hidden\" name=\"");
            writer.append(UtilHttp.makeCompositeParam(paramName, "compositeType"));
            writer.append("\" value=\"Timestamp\">");
        }

		this.appendTooltip(writer, context, modelFormField);
    }

    /* (non-Javadoc)
     * @see org.ofbiz.widget.form.FormStringRenderer#renderDropDownField(java.io.Writer, java.util.Map, org.ofbiz.widget.form.ModelFormField.DropDownField)
     */
    public void renderDropDownField(Appendable writer, Map<String, Object> context, DropDownField dropDownField) throws IOException {
        ModelFormField modelFormField = dropDownField.getModelFormField();
        ModelForm modelForm = modelFormField.getModelForm();

        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);

        writer.append("<select");
		appendClassNames(writer, context, modelFormField);
		writer.append(" name=\"");
		writer.append(modelFormField.getParameterName(context));
		writer.append('"');

        String idName = modelFormField.getIdName();
        if (UtilValidate.isNotEmpty(idName)) {
        	writer.append(" id=\"");
        	writer.append(idName);
        	writer.append('"');
        }

        if (dropDownField.isAllowMultiple()) {
        	writer.append(" multiple=\"multiple\"");
        }
        
        int otherFieldSize = dropDownField.getOtherFieldSize();
        String otherFieldName = dropDownField.getParameterNameOther(context);
        if (otherFieldSize > 0) {
            //buffer.append(" onchange=\"alert('ONCHANGE, process_choice:' + process_choice)\"");
            //buffer.append(" onchange='test_js()' ");
        	writer.append(" onchange=\"process_choice(this,document.");
        	writer.append(modelForm.getName());
        	writer.append(".");
        	writer.append(otherFieldName);
        	writer.append(")\" "); 
        }


        if (UtilValidate.isNotEmpty(event) && UtilValidate.isNotEmpty(action)) {
        	writer.append(" ");
        	writer.append(event);
        	writer.append("=\"");
        	writer.append(action);
        	writer.append('"');
        }

        writer.append(" size=\"").append(dropDownField.getSize()).append("\">");

        String currentValue = modelFormField.getEntry(context);
        if (UtilValidate.isEmpty(currentValue)) {
        	currentValue = String.valueOf(getEntityFieldValueForJBPM(context, modelFormField));
        }
        List<ModelFormField.OptionValue> allOptionValues = dropDownField.getAllOptionValues(context, modelForm.getDelegator(context));
        
        // This paragraph is for JBPM transitions drop down
        if (dropDownField.getModelFormField().getName().equals("transitionId")) {
        	Map parameters = (Map) context.get("parameters");
        	String taskInstanceId = (String) parameters.get("taskInstanceId");
    		if (UtilValidate.isNotEmpty(taskInstanceId)) {
            	List transitions = new OFBizJBPMTaskBean(Long.parseLong(taskInstanceId)).getAvailableTransitions();
            	if (transitions.size() > 0) {
            		allOptionValues.clear();
                    for (int j=0; j<transitions.size(); j++) {
                    	Transition transition = (Transition) transitions.get(j);
                    	OptionValue optionValue = new OptionValue(String.valueOf(transition.getId()), transition.getName());
                    	allOptionValues.add(optionValue);
                    }
            	}
    		}
        }

        // if the current value should go first, stick it in
        if (UtilValidate.isNotEmpty(currentValue) && "first-in-list".equals(dropDownField.getCurrent())) {
        	writer.append("<option");
        	writer.append(" selected=\"selected\"");
        	writer.append(" value=\"");
        	writer.append(currentValue);
        	writer.append("\">");
            String explicitDescription = dropDownField.getCurrentDescription(context);
            if (UtilValidate.isNotEmpty(explicitDescription)) {
            	writer.append(explicitDescription);
            } else {
            	writer.append(ModelFormField.FieldInfoWithOptions.getDescriptionForOptionKey(currentValue, allOptionValues));
            }
            writer.append("</option>");

            // add a "separator" option
            writer.append("<option value=\"");
            writer.append(currentValue);
            writer.append("\">---</option>");
        }

        // if allow empty is true, add an empty option
        if (dropDownField.isAllowEmpty()) {
        	writer.append("<option value=\"\">&nbsp;</option>");
        }

        // list out all options according to the option list
        Iterator<ModelFormField.OptionValue> optionValueIter = allOptionValues.iterator();
        while (optionValueIter.hasNext()) {
            ModelFormField.OptionValue optionValue = (ModelFormField.OptionValue) optionValueIter.next();
            String noCurrentSelectedKey = dropDownField.getNoCurrentSelectedKey(context);
            writer.append("<option");
            // if current value should be selected in the list, select it
            if (UtilValidate.isNotEmpty(currentValue) && currentValue.equals(optionValue.getKey()) && "selected".equals(dropDownField.getCurrent())) {
            	writer.append(" selected=\"selected\"");
            } else if (UtilValidate.isEmpty(currentValue) && noCurrentSelectedKey != null && noCurrentSelectedKey.equals(optionValue.getKey())) {
            	writer.append(" selected=\"selected\"");
            }
            writer.append(" value=\"");
            writer.append(optionValue.getKey());
            writer.append("\">");
            writer.append(optionValue.getDescription());
            writer.append("</option>");
        }

        writer.append("</select>");

        // Adapted from work by Yucca Korpela
        // http://www.cs.tut.fi/~jkorpela/forms/combo.html
        if (otherFieldSize > 0) {
        
            String fieldName = modelFormField.getParameterName(context);
            Map dataMap = modelFormField.getMap(context);
            if (dataMap == null) {
                dataMap = context;
            }
            Object otherValueObj = dataMap.get(otherFieldName);
            String otherValue = (otherValueObj == null) ? "" : otherValueObj.toString();
            
            writer.append("<noscript>");
            writer.append("<input type='text' name='");
            writer.append(otherFieldName);
            writer.append("'/> ");
            writer.append("</noscript>");
            writer.append("\n<script type='text/javascript' language='JavaScript'><!--");
            writer.append("\ndisa = ' disabled';");
            writer.append("\nif(other_choice(document.");
            writer.append(modelForm.getName());
            writer.append(".");
            writer.append(fieldName);
            writer.append(")) disa = '';");
            writer.append("\ndocument.write(\"<input type=");
            writer.append("'text' name='");
            writer.append(otherFieldName);
            writer.append("' value='");
            writer.append(otherValue);
            writer.append("' size='");
            writer.append(String.valueOf(otherFieldSize));
            writer.append("' ");
            writer.append("\" +disa+ \" onfocus='check_choice(document.");
            writer.append(modelForm.getName());
            writer.append(".");
            writer.append(fieldName);
            writer.append(")'/>\");");
            writer.append("\nif(disa && document.styleSheets)");
            writer.append(" document.");
            writer.append(modelForm.getName());
            writer.append(".");
            writer.append(otherFieldName);
            writer.append(".style.visibility  = 'hidden';");
            writer.append("\n//--></script>");
        }
		this.makeHyperlinkString(writer, dropDownField.getSubHyperlink(), context);

		this.appendTooltip(writer, context, modelFormField);
    }

    /* (non-Javadoc)
     * @see org.ofbiz.widget.form.FormStringRenderer#renderCheckField(java.io.Writer, java.util.Map, org.ofbiz.widget.form.ModelFormField.CheckField)
     */
    public void renderCheckField(Appendable writer, Map<String, Object> context, CheckField checkField) throws IOException {
        ModelFormField modelFormField = checkField.getModelFormField();
        ModelForm modelForm = modelFormField.getModelForm();

        String currentValue = modelFormField.getEntry(context);
        if (UtilValidate.isEmpty(currentValue)) {
        	currentValue = String.valueOf(getEntityFieldValueForJBPM(context, modelFormField));
        }
        
        Boolean allChecked = checkField.isAllChecked(context);
        
        List<ModelFormField.OptionValue> allOptionValues = checkField.getAllOptionValues(context, modelForm.getDelegator(context));
        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);

        // list out all options according to the option list
        Iterator<ModelFormField.OptionValue> optionValueIter = allOptionValues.iterator();
        while (optionValueIter.hasNext()) {
            ModelFormField.OptionValue optionValue = (ModelFormField.OptionValue) optionValueIter.next();

            writer.append("<input type=\"checkbox\"");
			appendClassNames(writer, context, modelFormField);
            
            // if current value should be selected in the list, select it
            if (Boolean.TRUE.equals(allChecked)) {
            	writer.append(" checked=\"checked\"");
            } else if (Boolean.FALSE.equals(allChecked)) {
                // do nothing
            } else if (UtilValidate.isNotEmpty(currentValue) && currentValue.equals(optionValue.getKey())) {
            	writer.append(" checked=\"checked\"");
            }
            writer.append(" name=\"");
            writer.append(modelFormField.getParameterName(context));
            writer.append('"');
            writer.append(" value=\"");
            writer.append(optionValue.getKey());
            writer.append("\"");

            if (UtilValidate.isNotEmpty(event) && UtilValidate.isNotEmpty(action)) {
            	writer.append(" ");
            	writer.append(event);
            	writer.append("=\"");
            	writer.append(action);
            	writer.append('"');
            }
            
            writer.append("/>");

            writer.append(optionValue.getDescription());
        }

		this.appendTooltip(writer, context, modelFormField);
    }

    /* (non-Javadoc)
     * @see org.ofbiz.widget.form.FormStringRenderer#renderRadioField(java.io.Writer, java.util.Map, org.ofbiz.widget.form.ModelFormField.RadioField)
     */
    public void renderRadioField(Appendable writer, Map<String, Object> context, RadioField radioField) throws IOException {
        ModelFormField modelFormField = radioField.getModelFormField();
        ModelForm modelForm = modelFormField.getModelForm();
        List<ModelFormField.OptionValue> allOptionValues = radioField.getAllOptionValues(context, modelForm.getDelegator(context));
        String currentValue = modelFormField.getEntry(context);
        if (UtilValidate.isEmpty(currentValue)) {
        	currentValue = String.valueOf(getEntityFieldValueForJBPM(context, modelFormField));
        }
        
        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);

        // list out all options according to the option list
        Iterator<ModelFormField.OptionValue> optionValueIter = allOptionValues.iterator();
        while (optionValueIter.hasNext()) {
            ModelFormField.OptionValue optionValue = (ModelFormField.OptionValue) optionValueIter.next();

            writer.append("<div");
			appendClassNames(writer, context, modelFormField);
			writer.append("><input type=\"radio\"");
            
            // if current value should be selected in the list, select it
            String noCurrentSelectedKey = radioField.getNoCurrentSelectedKey(context);
            if (UtilValidate.isNotEmpty(currentValue) && currentValue.equals(optionValue.getKey())) {
            	writer.append(" checked=\"checked\"");
            } else if (UtilValidate.isEmpty(currentValue) && noCurrentSelectedKey != null && noCurrentSelectedKey.equals(optionValue.getKey())) {
            	writer.append(" checked=\"checked\"");
            }
            writer.append(" name=\"");
            writer.append(modelFormField.getParameterName(context));
            writer.append('"');
            writer.append(" value=\"");
            writer.append(optionValue.getKey());
            writer.append("\"");

            if (UtilValidate.isNotEmpty(event) && UtilValidate.isNotEmpty(action)) {
            	writer.append(" ");
            	writer.append(event);
            	writer.append("=\"");
            	writer.append(action);
            	writer.append('"');
            }
            
            writer.append("/>");

            writer.append(optionValue.getDescription());
            writer.append("</div>");
        }

		this.appendTooltip(writer, context, modelFormField);
    }

    /* (non-Javadoc)
     * @see org.ofbiz.widget.form.FormStringRenderer#renderHiddenField(java.io.Writer, java.util.Map, org.ofbiz.widget.form.ModelFormField.HiddenField)
     */
    public void renderHiddenField(Appendable writer, Map<String, Object> context, HiddenField hiddenField) throws IOException {
        ModelFormField modelFormField = hiddenField.getModelFormField();
        String value = hiddenField.getValue(context);
        if (UtilValidate.isEmpty(value)) {
        	value = String.valueOf(getEntityFieldValueForJBPM(context, modelFormField));
        }
        this.renderHiddenField(writer, context, modelFormField, value);
    }
}
