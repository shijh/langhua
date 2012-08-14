/*
 * This library is part of OpenCms Identity module of Langhua Opensource Foundation
 *
 * Copyright (C) 2009  Langhua Opensource Foundation (http://langhua.org)
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
 * project website: http://langhua.org/opensource/opencms/opencms-identity/
 * 
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.opencms.identity.sso.cas;

import org.apache.commons.logging.Log;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jasig.cas.client.authentication.AttributePrincipal;
import org.jasig.cas.client.authentication.AttributePrincipalImpl;
import org.jasig.cas.client.proxy.Cas20ProxyRetriever;
import org.jasig.cas.client.proxy.ProxyGrantingTicketStorage;
import org.jasig.cas.client.proxy.ProxyRetriever;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.XmlUtils;
import org.jasig.cas.client.validation.AbstractCasProtocolUrlBasedTicketValidator;
import org.jasig.cas.client.validation.Assertion;
import org.jasig.cas.client.validation.AssertionImpl;
import org.jasig.cas.client.validation.TicketValidationException;
import org.opencms.main.CmsLog;

import org.langhua.opencms.identity.commons.A_CmsAuthenticationHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copied from cas-java-client.
 * 
 * Add a configurable serviceValidate parameter.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public class Cas20ServiceTicketValidator extends AbstractCasProtocolUrlBasedTicketValidator {

	public final Log LOG = CmsLog.getLog(A_CmsAuthenticationHandler.class);

	/** The CAS 2.0 protocol proxy callback url. */
    private String proxyCallbackUrl;

    /** The storage location of the proxy granting tickets. */
    private ProxyGrantingTicketStorage proxyGrantingTicketStorage;

    /** Implementation of the proxy retriever. */
    private ProxyRetriever proxyRetriever;
    
    private String casServiceValidate;

    /**
     * Constructs an instance of the CAS 2.0 Service Ticket Validator with the supplied
     * CAS server url prefix.
     *
     * @param casServerUrlPrefix the CAS Server URL prefix.
     */
    public Cas20ServiceTicketValidator(final String casServerUrlPrefix, final String casServiceValidate) {
        super(casServerUrlPrefix);
        this.proxyRetriever = new Cas20ProxyRetriever(casServerUrlPrefix);
        this.casServiceValidate = casServiceValidate;
    }

    /**
     * Adds the pgtUrl to the list of parameters to pass to the CAS server.
     *
     * @param urlParameters the Map containing the existing parameters to send to the server.
     */
    protected final void populateUrlAttributeMap(final Map urlParameters) {
        urlParameters.put("pgtUrl", encodeUrl(this.proxyCallbackUrl));
    }

    protected String getUrlSuffix() {
        return this.casServiceValidate;
    }

    protected final Assertion parseResponseFromServer(final String response) throws TicketValidationException {
    	LOG.debug(response);
        final String error = XmlUtils.getTextForElement(response,
                "authenticationFailure");

        if (CommonUtils.isNotBlank(error)) {
            throw new TicketValidationException(error);
        }

        final String principal = XmlUtils.getTextForElement(response, "user");
        final String proxyGrantingTicketIou = XmlUtils.getTextForElement(
                response, "proxyGrantingTicket");
        final String proxyGrantingTicket = this.proxyGrantingTicketStorage != null ? this.proxyGrantingTicketStorage.retrieve(proxyGrantingTicketIou) : null;

        if (CommonUtils.isEmpty(principal)) {
            throw new TicketValidationException("No principal was found in the response from the CAS server.");
        }

        final Assertion assertion;
        final Map<String, String> attributes = extractCustomAttributes(response);
        if (CommonUtils.isNotBlank(proxyGrantingTicket)) {
            final AttributePrincipal attributePrincipal = new AttributePrincipalImpl(principal, attributes, proxyGrantingTicket, this.proxyRetriever);
            assertion = new AssertionImpl(attributePrincipal);
        } else {
            assertion = new AssertionImpl(new AttributePrincipalImpl(principal, attributes));
        }

        customParseResponse(response, assertion);

        return assertion;
    }
    
    protected Map<String, String> extractCustomAttributes(final String xml) {
    	Document doc;
    	try {
			doc = DocumentHelper.parseText(xml);
		} catch (DocumentException e) {
    		return Collections.emptyMap();
		}
		LOG.debug(doc.asXML());
    	final Map<String, String> attributes = new HashMap<String, String>();
    	List<Element> attributeElements;
    	try {
    		attributeElements = ((Element) ((Element) doc.getRootElement().elements().get(0)).elements().get(1)).elements();
    	} catch (IndexOutOfBoundsException e) {
    		return Collections.emptyMap();
    	}
		for (int i=0; i<attributeElements.size(); i++) {
			Element attribute = attributeElements.get(i);
			LOG.debug("Attribute: " + attribute.element("name").getStringValue() + " - " + attribute.element("value").getStringValue());
			attributes.put(attribute.element("name").getStringValue(), attribute.element("value").getStringValue());
		}
    	return attributes;
    }

    /**
     * Template method if additional custom parsing (such as Proxying) needs to be done.
     *
     * @param response the original response from the CAS server.
     * @param assertion the partially constructed assertion.
     * @throws TicketValidationException if there is a problem constructing the Assertion.
     */
    protected void customParseResponse(final String response, final Assertion assertion) throws TicketValidationException {
        // nothing to do
    }

    public final void setProxyCallbackUrl(final String proxyCallbackUrl) {
        this.proxyCallbackUrl = proxyCallbackUrl;
    }

    public final void setProxyGrantingTicketStorage(final ProxyGrantingTicketStorage proxyGrantingTicketStorage) {
        this.proxyGrantingTicketStorage = proxyGrantingTicketStorage;
    }

    public final void setProxyRetriever(final ProxyRetriever proxyRetriever) {
        this.proxyRetriever = proxyRetriever;
    }    
}
