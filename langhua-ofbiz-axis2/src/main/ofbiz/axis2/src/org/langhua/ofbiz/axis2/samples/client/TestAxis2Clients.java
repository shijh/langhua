package org.langhua.ofbiz.axis2.samples.client;

import java.io.File;
import java.rmi.RemoteException;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.neethi.Policy;
import org.apache.neethi.PolicyEngine;
import org.apache.rampart.RampartMessageData;
import org.apache.ws.axis2.mtomsample.MTOMSampleMTOMSampleSOAP11Port_httpStub;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.FileUtil;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.common.ApplicationParamUtil;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.ServiceUtil;

import samples.quickstart.service.xmlbeans.xsd.GetPriceDocument;
import samples.quickstart.service.xmlbeans.xsd.GetPriceResponseDocument;
import samples.quickstart.service.xmlbeans.xsd.UpdateDocument;

public class TestAxis2Clients {
    public static final String module = ApplicationParamUtil.class.getName();
    
    private static EndpointReference targetEPR = new EndpointReference("http://localhost:8080/axis2/services/Version");
    
    private static final String defaultMethod = "getVersion";
    
    private static final String defaultNameSpace = "http://axisversion.sample";
    
    private static final String defaultStockEPR = "http://localhost:8080/axis2/services/StockQuoteService";

    /**
     * Generic Test SOAP Service
     * @param dctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testAxis2SOAP(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> response = ServiceUtil.returnSuccess();
        String target = (String) context.get("targetEPR");
        String action = (String) context.get("action");
        String nameSpace = (String) context.get("nameSpace");
        String method = (String) context.get("method");
        String localPort = (String) context.get("localPort");
        Map<String, String> parameters = (Map<String, String>) context.get("parameters");
        setSSLProperties(target);

        try {
            OMElement payload = getOMElement(nameSpace, localPort, method, parameters);
            Debug.logInfo(payload.toString(), module);
            ServiceClient sender = new ServiceClient();

            Options options = new Options();
            sender.setOptions(options);
            if (UtilValidate.isEmpty(target)) {
                options.setTo(targetEPR);
            } else {
                options.setTo(new EndpointReference(target));
            }
            if (UtilValidate.isEmpty(action)) {
                options.setAction(action);
            }
            OMElement result = sender.sendReceive(payload);
            Debug.logInfo(result.toString(), module);
            response.put("response", result.toString());
        } catch (AxisFault axisFault) {
        	Debug.logError(axisFault.getMessage(), module);
            return ServiceUtil.returnError(axisFault.getMessage());
        }
        return response;
    }

    private static OMElement getOMElement(String nameSpace, String localPort, String method, Map<String, String> parameters) {
        OMFactory fac = OMAbstractFactory.getOMFactory();
        OMNamespace omNs = fac.createOMNamespace(
               (UtilValidate.isEmpty(nameSpace) ? defaultNameSpace : nameSpace),
               (UtilValidate.isEmpty(localPort) ? "" : localPort));
        OMElement methodElement = fac.createOMElement((UtilValidate.isEmpty(method) ? defaultMethod : method),
        		omNs);
        if (UtilValidate.isNotEmpty(parameters)) {
        	for (String key : parameters.keySet()) {
                OMElement value = fac.createOMElement(key, omNs);
                value.addChild(fac.createOMText(value, parameters.get(key)));
        		methodElement.addChild(value);
        	}
        }
        return methodElement;
    }

    /**
     * Test Axis2 Quick Start Sample: Stock Quote Service, Update stock price method (ADB)
     * @param dctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testAxis2ADBUpdateStockPrice(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> response = ServiceUtil.returnSuccess();
        String symbol = (String) context.get("symbol");
        Double price = (Double) context.get("price");
        String target = (String) context.get("targetEPR");
        if (UtilValidate.isEmpty(target)) {
        	target = defaultStockEPR;
        }
        setSSLProperties(target);

        try {
        	samples.quickstart.service.adb.StockQuoteServiceStub stub = new samples.quickstart.service.adb.StockQuoteServiceStub(target);
        	samples.quickstart.service.adb.StockQuoteServiceStub.Update req = new samples.quickstart.service.adb.StockQuoteServiceStub.Update();
            req.setSymbol(symbol);
            req.setPrice(price);

            stub.update(req);
            response.put("response", "Price updated.");
        } catch (Exception e) {
        	Debug.logError(e.toString(), module);
            return ServiceUtil.returnError(e.getMessage());
		}
        return response;
    }

	/**
     * Test Axis2 Quick Start Sample: Stock Quote Service, Get stock price method (ADB)
     * @param dctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testAxis2ADBGetStockPrice(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> response = ServiceUtil.returnSuccess();
        String symbol = (String) context.get("symbol");
        String target = (String) context.get("targetEPR");
        if (UtilValidate.isEmpty(target)) {
        	target = defaultStockEPR;
        }
        setSSLProperties(target);

        try {
        	samples.quickstart.service.adb.StockQuoteServiceStub stub = new samples.quickstart.service.adb.StockQuoteServiceStub(target);
        	samples.quickstart.service.adb.StockQuoteServiceStub.GetPrice req = new samples.quickstart.service.adb.StockQuoteServiceStub.GetPrice();
            req.setSymbol(symbol);

            samples.quickstart.service.adb.StockQuoteServiceStub.GetPriceResponse res = stub.getPrice(req);
            response.put("response", res.get_return());
        } catch (Exception e) {
        	Debug.logError(e.toString(), module);
            return ServiceUtil.returnError(e.getMessage());
		}
        return response;
    }

    /**
     * Test Axis2 Quick Start Sample: Stock Quote Service, Update stock price method (Axiom)
     * @param dctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testAxis2AxiomUpdateStockPrice(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> response = ServiceUtil.returnSuccess();
        String symbol = (String) context.get("symbol");
        Double price = (Double) context.get("price");
        String target = (String) context.get("targetEPR");
        if (UtilValidate.isEmpty(target)) {
        	target = defaultStockEPR;
        }
        setSSLProperties(target);

        try {
            OMElement updatePayload = updatePayload(symbol, price);
            Debug.logInfo(updatePayload.toString(), module);
            
            Options options = new Options();
            options.setTo(new EndpointReference(target));
            options.setTransportInProtocol(Constants.TRANSPORT_HTTP);

            ServiceClient sender = new ServiceClient();
            sender.setOptions(options);

            sender.fireAndForget(updatePayload);
            response.put("response", "Price updated.");
        } catch (Exception e) {
        	Debug.logError(e.toString(), module);
            return ServiceUtil.returnError(e.getMessage());
		}
        return response;
    }

    /**
     * Test Axis2 Quick Start Sample: Stock Quote Service, Get stock price method (Axiom)
     * @param dctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testAxis2AxiomGetStockPrice(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> response = ServiceUtil.returnSuccess();
        String symbol = (String) context.get("symbol");
        String target = (String) context.get("targetEPR");
        if (UtilValidate.isEmpty(target)) {
        	target = defaultStockEPR;
        }
        setSSLProperties(target);

        try {
            OMElement getPricePayload = getPricePayload(symbol);
            Debug.logInfo(getPricePayload.toString(), module);
            
            Options options = new Options();
            options.setTo(new EndpointReference(target));
            options.setTransportInProtocol(Constants.TRANSPORT_HTTP);

            ServiceClient sender = new ServiceClient();
            sender.setOptions(options);
            OMElement result = sender.sendReceive(getPricePayload);

            String resultRes = result.getFirstElement().getText();
            response.put("response", resultRes);
        } catch (Exception e) {
        	Debug.logError(e.toString(), module);
            return ServiceUtil.returnError(e.getMessage());
		}
        return response;
    }

    private static OMElement getPricePayload(String symbol) {
        OMFactory fac = OMAbstractFactory.getOMFactory();
        OMNamespace omNs = fac.createOMNamespace("http://quickstart.samples/xsd", "tns");

        OMElement method = fac.createOMElement("getPrice", omNs);
        OMElement value = fac.createOMElement("symbol", omNs);
        value.addChild(fac.createOMText(value, symbol));
        method.addChild(value);
        return method;
    }

    private static OMElement updatePayload(String symbol, double price) {
        OMFactory fac = OMAbstractFactory.getOMFactory();
        OMNamespace omNs = fac.createOMNamespace("http://quickstart.samples/xsd", "tns");

        OMElement method = fac.createOMElement("update", omNs);

        OMElement value1 = fac.createOMElement("symbol", omNs);
        value1.addChild(fac.createOMText(value1, symbol));
        method.addChild(value1);

        OMElement value2 = fac.createOMElement("price", omNs);
        value2.addChild(fac.createOMText(value2,
                                         Double.toString(price)));
        method.addChild(value2);
        return method;
    }

    /**
     * Test Axis2 Quick Start Sample: Stock Quote Service, Update stock price method (JiBX)
     * @param dctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testAxis2JiBXUpdateStockPrice(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> response = ServiceUtil.returnSuccess();
        String symbol = (String) context.get("symbol");
        Double price = (Double) context.get("price");
        String target = (String) context.get("targetEPR");
        if (UtilValidate.isEmpty(target)) {
        	target = defaultStockEPR;
        }
        setSSLProperties(target);

        try {
        	samples.quickstart.service.jibx.StockQuoteServiceStub stub = new samples.quickstart.service.jibx.StockQuoteServiceStub(target);
            stub.update(symbol, price);
            response.put("response", "Price updated.");
        } catch (Exception e) {
        	Debug.logError(e.toString(), module);
            return ServiceUtil.returnError(e.getMessage());
		}
        return response;
    }

    /**
     * Test Axis2 Quick Start Sample: Stock Quote Service, Get stock price method (JiBX)
     * @param dctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testAxis2JiBXGetStockPrice(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> response = ServiceUtil.returnSuccess();
        String symbol = (String) context.get("symbol");
        String target = (String) context.get("targetEPR");
        if (UtilValidate.isEmpty(target)) {
        	target = defaultStockEPR;
        }
        setSSLProperties(target);

        try {
        	samples.quickstart.service.jibx.StockQuoteServiceStub stub = new samples.quickstart.service.jibx.StockQuoteServiceStub(target);
            Double res = stub.getPrice(symbol);
            response.put("response", res.toString());
        } catch (Exception e) {
        	Debug.logError(e.toString(), module);
            return ServiceUtil.returnError(e.getMessage());
		}
        return response;
    }

    /**
     * Test Axis2 Quick Start Sample: Stock Quote Service, Update stock price method (xmlbeans)
     * @param dctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testAxis2XmlbeansUpdateStockPrice(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> response = ServiceUtil.returnSuccess();
        String symbol = (String) context.get("symbol");
        Double price = (Double) context.get("price");
        String target = (String) context.get("targetEPR");
        if (UtilValidate.isEmpty(target)) {
        	target = defaultStockEPR;
        }
        setSSLProperties(target);

        try {
        	samples.quickstart.service.xmlbeans.StockQuoteServiceStub stub = new samples.quickstart.service.xmlbeans.StockQuoteServiceStub(target);
            UpdateDocument reqDoc = UpdateDocument.Factory.newInstance();
            UpdateDocument.Update req = reqDoc.addNewUpdate();
            req.setSymbol(symbol);
            req.setPrice(price);

            stub.update(reqDoc);
            response.put("response", "Price updated.");
        } catch (Exception e) {
        	Debug.logError(e.toString(), module);
            return ServiceUtil.returnError(e.getMessage());
		}
        return response;
    }

    /**
     * Test Axis2 Quick Start Sample: Stock Quote Service, Get stock price method (xmlbeans)
     * @param dctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testAxis2XmlbeansGetStockPrice(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> response = ServiceUtil.returnSuccess();
        String symbol = (String) context.get("symbol");
        String target = (String) context.get("targetEPR");
        if (UtilValidate.isEmpty(target)) {
        	target = defaultStockEPR;
        }
        setSSLProperties(target);

        try {
        	samples.quickstart.service.xmlbeans.StockQuoteServiceStub stub = new samples.quickstart.service.xmlbeans.StockQuoteServiceStub(target);
            GetPriceDocument reqDoc = GetPriceDocument.Factory.newInstance();
            GetPriceDocument.GetPrice req = reqDoc.addNewGetPrice();
            req.setSymbol(symbol);

            GetPriceResponseDocument res = stub.getPrice(reqDoc);
            response.put("response", res.getGetPriceResponse().getReturn());
        } catch (Exception e) {
        	Debug.logError(e.toString(), module);
            return ServiceUtil.returnError(e.getMessage());
		}
        return response;
    }

    private static void setSSLProperties(String target) {
    	if (target.toLowerCase().startsWith("https://")) {
    		String trustStore = UtilProperties.getPropertyValue("axis2client", "axis2.client.ssl.trustStore", "framework/base/config/ofbizssl.jks");
    		String trustStorePassword = UtilProperties.getPropertyValue("axis2client", "axis2.client.ssl.trustStorePassword", "changeit");
            System.setProperty("javax.net.ssl.trustStore", FileUtil.getFile(trustStore).getAbsolutePath());
            System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
    	}
	}

    /**
     * Test Axis2 Rampart Policy Sample
     * @param dctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testAxis2RampartPolicySample(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> response = ServiceUtil.returnSuccess();
        String sample = (String) context.get("sample");
        if (UtilValidate.isEmpty(sample)) {
        	sample = "sample01";
        }
        String target = (String) context.get("targetEPR");
        if (UtilValidate.isEmpty(target)) {
        	target = "http://localhost:8080/axis2/services/" + sample;
        }
        setSSLProperties(target);

        try {
            ConfigurationContext ctx = ConfigurationContextFactory.createConfigurationContextFromFileSystem(FileUtil.getFile("hot-deploy/axis2/config/rampart").getAbsolutePath(), null);
            
            ServiceClient client = new ServiceClient(ctx, null);
            Options options = new Options();
            options.setAction("urn:echo");
            options.setTo(new EndpointReference(target));
            options.setProperty(RampartMessageData.KEY_RAMPART_POLICY,  loadPolicy(FileUtil.getFile("hot-deploy/axis2/config/rampart/" + sample + "/policy.xml").getAbsolutePath()));
            client.setOptions(options);
            
            client.engageModule("addressing");
            client.engageModule("rampart");

            OMElement res = client.sendReceive(getPayload("Hello world", sample));
            Debug.logInfo(res.toString(), module);
        } catch (Exception e) {
        	Debug.logError(e.toString(), module);
            return ServiceUtil.returnError(e.getMessage());
		}
        return response;
    }

    private static Policy loadPolicy(String xmlPath) throws Exception {
        StAXOMBuilder builder = new StAXOMBuilder(xmlPath);
        return PolicyEngine.getPolicy(builder.getDocumentElement());
    }
    
    private static OMElement getPayload(String value, String sample) {
        OMFactory factory = OMAbstractFactory.getOMFactory();
        OMNamespace ns = factory.createOMNamespace("http://" + sample + ".policy.samples.rampart.apache.org","ns1");
        OMElement elem = factory.createOMElement("echo", ns);
        OMElement childElem = factory.createOMElement("param0", ns);
        childElem.setText(value);
        elem.addChild(childElem);
        
        return elem;
    }

    /**
     * Test Axis2 Rampart MTOM Sample
     * @param dctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testAxis2RampartMTOMSample(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> response = ServiceUtil.returnSuccess();
        String sample = (String) context.get("sample");
        if (UtilValidate.isEmpty(sample)) {
        	sample = "sample03";
        }
        String file = (String) context.get("file");
        String dest = (String) context.get("dest");
        String target = (String) context.get("targetEPR");
        if (UtilValidate.isEmpty(target)) {
        	target = "http://localhost:8080/axis2/services/MTOMSample";
        }
        setSSLProperties(target);

        try {
            File sourceFile = FileUtil.getFile(file);
            transferFile(sourceFile, dest, sample, target);
        } catch (Exception e) {
        	Debug.logError(e.toString(), module);
            return ServiceUtil.returnError(e.getMessage());
		}
        return response;
    }

	private static void transferFile(File file, String destination, String sample, String target)
			throws Exception {
        ConfigurationContext ctx = ConfigurationContextFactory.createConfigurationContextFromFileSystem(FileUtil.getFile("hot-deploy/axis2/config/rampart").getAbsolutePath(), null);
		MTOMSampleMTOMSampleSOAP11Port_httpStub serviceStub = new MTOMSampleMTOMSampleSOAP11Port_httpStub(
				ctx,
				target
		);

		ServiceClient client = serviceStub._getServiceClient();
		Options options = client.getOptions();
		// Enable MTOM in the client side
		options.setProperty(
				Constants.Configuration.ENABLE_MTOM, Constants.VALUE_TRUE);
		//Increase the time out when sending large attachments
		options.setTimeOutInMilliSeconds(10000);
        options.setProperty(RampartMessageData.KEY_RAMPART_POLICY,  loadPolicy(FileUtil.getFile("hot-deploy/axis2/config/rampart/" + sample + "/policy.xml").getAbsolutePath()));
        client.setOptions(options);
        
        client.engageModule("addressing");
        client.engageModule("rampart");

		// Populating the code generated beans
		MTOMSampleMTOMSampleSOAP11Port_httpStub.AttachmentRequest attachmentRequest = new MTOMSampleMTOMSampleSOAP11Port_httpStub.AttachmentRequest();
		MTOMSampleMTOMSampleSOAP11Port_httpStub.AttachmentType attachmentType = new MTOMSampleMTOMSampleSOAP11Port_httpStub.AttachmentType();
		MTOMSampleMTOMSampleSOAP11Port_httpStub.Base64Binary base64Binary = new MTOMSampleMTOMSampleSOAP11Port_httpStub.Base64Binary();

		// Creating a javax.activation.FileDataSource from the input file.
		FileDataSource fileDataSource = new FileDataSource(file);

		// Create a dataHandler using the fileDataSource. Any implementation of
		// javax.activation.DataSource interface can fit here.
		DataHandler dataHandler = new DataHandler(fileDataSource);
		base64Binary.setBase64Binary(dataHandler);
        MTOMSampleMTOMSampleSOAP11Port_httpStub.ContentType_type0 param = new MTOMSampleMTOMSampleSOAP11Port_httpStub.ContentType_type0();
        param.setContentType_type0(dataHandler.getContentType());
        base64Binary.setContentType(param);
		attachmentType.setBinaryData(base64Binary);
		attachmentType.setFileName(destination);
		attachmentRequest.setAttachmentRequest(attachmentType);

		MTOMSampleMTOMSampleSOAP11Port_httpStub.AttachmentResponse response = serviceStub.attachment(attachmentRequest);
		Debug.logInfo(response.getAttachmentResponse(), module);
	}
}
