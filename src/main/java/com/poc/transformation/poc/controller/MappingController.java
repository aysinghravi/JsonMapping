package com.poc.transformation.poc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.poc.transformation.poc.model.TestModel;
import com.poc.transformation.poc.service.MappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import reactor.core.publisher.Mono;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;

@Slf4j
@RestController
@RequestMapping("/v1")
public class MappingController {
    private final MappingService service;

    public MappingController(MappingService service) {
        this.service = service;
    }

    @PostMapping
    public Mono<JsonNode> transformMapping(@RequestBody TestModel testModel) {
        log.info("Inside controller");
        return service.mapTransform(testModel);
    }

    @PostMapping("/test")
    public Mono<String> testXml(@RequestBody String xmlstring) {
        Document doc = convertStringToDocument(xmlstring);
        log.info("Document received: {}", doc);

        // Accessing tags separately
//        logXmlTags(doc.getDocumentElement(), "");

        log.info("groupId: {}", doc.getDocumentElement().getAttribute("groupId"));
        log.info("elements: {}", doc.getDocumentElement());

        String str = convertDocumentToString(doc);
        log.info("String representation of XML: {}", str);
        return Mono.just(str);
    }

    private static void logXmlTags(Node node, String indent) {
        // Log the current node name
        log.info("{}{}", indent, node.getNodeName());

        // Log the attributes of the current node
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                log.info("{}{}={} (Attribute)", indent, attribute.getNodeName(), attribute.getNodeValue());
            }
        }

        // Recursively log child nodes
        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                logXmlTags(childNode, indent + "  ");
            }
        }
    }

    private static String convertDocumentToString(Document doc) {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer;
        try {
            transformer = tf.newTransformer();
            // below code to remove XML declaration
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            String output = writer.getBuffer().toString();
            return output;
        } catch (TransformerException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static Document convertStringToDocument(String xmlStr) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlStr)));
            return doc;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
