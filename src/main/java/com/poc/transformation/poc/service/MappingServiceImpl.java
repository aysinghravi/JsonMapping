package com.poc.transformation.poc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.poc.transformation.poc.model.TestModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MappingServiceImpl implements MappingService{
    private final ObjectMapper objectMapper;

    public MappingServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<JsonNode> mapTransform(TestModel testModel) {
        log.info("Initial data: {}", testModel);
        JsonNode finalJson = transform(testModel.getSourceJson(), testModel.getDestinationJsonTemplate());
        return Mono.just(finalJson);
    }

    private JsonNode transform(JsonNode sourceJson, JsonNode destinationTemplate) {
        log.info("Transforming and creating output json");
        if (destinationTemplate.isObject()) {
            log.info("Node is object node");
            return processObject((ObjectNode) destinationTemplate, sourceJson);
        } else if (destinationTemplate.isArray()) {
            log.info("Node is array node");
            destinationTemplate = handleArrayNode((ArrayNode) destinationTemplate, sourceJson);
            return processArray((ArrayNode) destinationTemplate, sourceJson);
        } else {
            log.info("Node is textual");
            return destinationTemplate;
        }
    }

    private JsonNode processObject(ObjectNode objectNode, JsonNode sourceJson) {
        ObjectNode result = objectMapper.createObjectNode();
        objectNode.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isTextual() && isPlaceholder(value.asText())) {
                log.info("value is textual: {}", value);
                result.put(key, resolvePlaceholder(value.asText(), sourceJson));
            } else if (value.isArray()) {
                log.info("value is array: {}", value);
                value = handleArrayNode((ArrayNode) value, sourceJson);
                ArrayNode processedArray = objectMapper.createArrayNode();
                value.elements().forEachRemaining(arrayElement -> processedArray.add(transform(sourceJson, arrayElement)));
                result.set(key, processedArray);
            } else {
                log.info("value is object: {}", value);
                result.set(key, transform(sourceJson, value));
            }
        });
        return result;
    }

    private JsonNode processArray(ArrayNode arrayNode, JsonNode sourceJson) {
        log.info("processing array node");
        ArrayNode result = objectMapper.createArrayNode();
        arrayNode.elements().forEachRemaining(arrayElement -> sourceJson.forEach(sourceElement -> result.add(transform(sourceElement, arrayElement))));
        return result;
    }

    private ArrayNode handleArrayNode(ArrayNode destinationNode, JsonNode sourceJson) {
        log.info("handling array nodes for node: {}", destinationNode);
        ArrayNode resultArray = objectMapper.createArrayNode();

        destinationNode.forEach(arrayElement -> {
            log.info("array element: {}", arrayElement);
            String listKey = null;

            if (arrayElement.isTextual()) {
                String placeholder = arrayElement.asText();
                log.info("placeholder here: {}", placeholder);
                int endIndex = placeholder.indexOf(".*");
                if (endIndex != -1) {
                    listKey = placeholder.substring(3, endIndex); // Extract substring before .*
                } else {
                    // Handle case when .* is not found
                    listKey = placeholder.substring(3); // Extract substring after {{$
                }

                String[] keys = listKey.split("\\.");
                log.info("keys: {}", keys);
                JsonNode listNode = sourceJson;

                for (final String key : keys) {
                    // This conditional block checks if the next path segment is an array index
                    if (key.matches("^-?\\d+$")) {
                        log.info("current index listNode: {}", key);
                        listNode = getListValue(listNode, key);
                    } else {
                        log.info("current listNode: {}", listNode);
                        listNode = listNode.findPath(key);
                    }
//                    if (listNode.isMissingNode()) {
//                        log.debug("listNode not found: {}", key);
//                        return new TextNode(key);            }
                }

                if (listNode != null && listNode.isArray()) {
                    int listSize = listNode.size();
                    for (int i = 0; i < listSize; i++) {
                        int finalI = i;
                        String currentListValue = listKey + ".*";
                        String replacementValue = listKey + "." + finalI;
                        String resolvedValue = resolvePlaceholder(arrayElement.asText().replace(currentListValue, replacementValue), sourceJson);
                        resultArray.add(resolvedValue);

                    }
                } else {
                    log.error("List node '{}' not found or is not an array in the source JSON.", listKey);
                }

            } else {
                // Iterate over all fields of the array node element to find the list key
                Iterator<Map.Entry<String, JsonNode>> fieldsIterator = arrayElement.fields();
                while (fieldsIterator.hasNext()) {
                    Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
                    JsonNode fieldValue = fieldEntry.getValue();
                    log.info("field value: {}", fieldValue);
                    if (fieldValue.isTextual() && isPlaceholder(fieldValue.asText())) {
                        String placeholder = fieldValue.asText();
                        log.info("placeholder here: {}", placeholder);
                        int endIndex = placeholder.indexOf(".*");
                        if (endIndex != -1) {
                            listKey = placeholder.substring(3, endIndex); // Extract substring before .*
                        } else {
                            // Handle case when .* is not found
                            listKey = placeholder.substring(3); // Extract substring after {{$
                        }
                        break; // Exit loop if list key is found
                    }
                }


                log.info("listkey: {}", listKey);

                ObjectNode processedNode = objectMapper.createObjectNode();

                if (listKey != null) {
                    // If list key is found, proceed with fetching the list node from source JSON
                    String[] keys = listKey.split("\\.");
                    log.info("keys: {}", keys);
                    JsonNode listNode = sourceJson;

                    for (final String key : keys) {
                        // This conditional block checks if the next path segment is an array index
                        if (key.matches("^-?\\d+$")) {
                            log.info("current index listNode: {}", key);
                            listNode = getListValue(listNode, key);
                        } else {
                            log.info("current listNode: {}", listNode);
                            listNode = listNode.findPath(key);
                        }
//                    if (listNode.isMissingNode()) {
//                        log.debug("listNode not found: {}", key);
//                        return new TextNode(key);            }
                    }
                    if (listNode != null ) {
                        int listSize = listNode.size();
                        for (int i = 0; i < listSize; i++) {
                            ObjectNode tempNode = objectMapper.createObjectNode();
                            int finalI = i;
                            String currentListValue = listKey + ".*";
                            String replacementValue = listKey + "." + finalI;
                            JsonNode finalListNode = listNode;
                            arrayElement.fields().forEachRemaining(elementEntry -> {
                                String elementKey = elementEntry.getKey();
                                JsonNode elementValue = elementEntry.getValue();
                                log.info("element key: {} element value: {}", elementKey, elementValue);
                                if (elementValue.isTextual() && isPlaceholder(elementValue.asText())) {
                                    String resolvedValue = resolvePlaceholder(elementValue.asText().replace(currentListValue, replacementValue), sourceJson);
                                    tempNode.put(elementKey, resolvedValue);
                                } else if (elementValue.isArray()) {
                                    log.info("ele val is array");
                                    String stringvalue = returnJsonString(elementValue);
                                    stringvalue = stringvalue.replaceAll(Pattern.quote(currentListValue), Matcher.quoteReplacement(replacementValue));
                                    log.info("string: {}", stringvalue);
                                    ArrayNode processedArray = handleArrayNode((ArrayNode) returnJsonObject(stringvalue), sourceJson);
                                    tempNode.set(elementKey, processedArray);
                                } else {
                                    tempNode.set(elementKey, elementValue);
                                }
                            });
                            resultArray.add(tempNode);
                        }
                    } else {
                        log.error("List node '{}' not found or is not an array in the source JSON.", listKey);
                    }
                } else {
                    // If list key is null, directly replace placeholders with values from the source JSON
                    arrayElement.fields().forEachRemaining(elementEntry -> {
                        String elementKey = elementEntry.getKey();
                        JsonNode elementValue = elementEntry.getValue();
                        if (elementValue.isTextual() && isPlaceholder(elementValue.asText())) {
                            String resolvedValue = resolvePlaceholder(elementValue.asText(), sourceJson);
                            processedNode.put(elementKey, resolvedValue);
                        } else {
                            processedNode.set(elementKey, elementValue);
                        }
                    });
                    resultArray.add(processedNode);
                }
            }});
        return resultArray;
    }

    private boolean isPlaceholder(String text) {
        return text.startsWith("{{") && text.endsWith("}}");
    }

    private String resolvePlaceholder(String placeholder, JsonNode sourceJson) {
        String[] pathSegments = placeholder.substring(3, placeholder.length() - 2).split("\\.");
        log.info("placeholder: {}", placeholder);
        JsonNode currentNode = sourceJson;

        for (final String pathSegment : pathSegments) {
            // This conditional block checks if the next path segment is an array index
            if (pathSegment.matches("^-?\\d+$")) {
                log.info("current index node: {}", pathSegment);
                currentNode = getListValue(currentNode, pathSegment);
            } else {
                log.info("current node: {}", currentNode);
                currentNode = currentNode.findPath(pathSegment);
            }
            if (currentNode.isMissingNode()) {
                log.debug("Node not found: {}", pathSegment);
                return new TextNode(placeholder ).asText();            }
        }

        log.info("returning value: {}", currentNode);
        return currentNode.asText();
    }

    private JsonNode getListValue(JsonNode payloadJson, String placeholder){
        log.debug("fetching list/array values");
        final int index = Integer.parseInt(placeholder);
        if (payloadJson.has(index)) {
            return payloadJson.get(index);
        } else {
            return new TextNode(placeholder);
        }
    }

    private JsonNode returnJsonObject(String jsonString) {
        try {
            return objectMapper.readTree(jsonString);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String returnJsonString(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
