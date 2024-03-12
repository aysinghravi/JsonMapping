package com.poc.transformation.poc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.poc.transformation.poc.model.TestModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
        ArrayNode resultArray = objectMapper.createArrayNode();

        destinationNode.forEach(arrayElement -> {
            log.info("array element: {}", arrayElement);
            String listKey = fetchListKey(arrayElement);

            if (listKey != null) {
                processArrayElementWithListKey(arrayElement, listKey, sourceJson)
                        .ifPresent(resultArray::addAll);
            } else {
                processArrayElementWithoutListKey(arrayElement, sourceJson)
                        .ifPresent(resultArray::add);
            }
        });

        return resultArray;
    }

    private String fetchListKey(JsonNode arrayElement) {
        log.info("extracting list key from: {}", arrayElement);
        String listKey = null;
        if (arrayElement.isTextual() && isPlaceholder(arrayElement.asText())) {
            listKey = extractListKey(arrayElement.asText());
        }
        Iterator<Map.Entry<String, JsonNode>> fieldsIterator = arrayElement.fields();
        while (fieldsIterator.hasNext()) {
            Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
            JsonNode fieldValue = fieldEntry.getValue();
            log.info("field value: {}", fieldValue);
            if (fieldValue.isTextual() && isPlaceholder(fieldValue.asText())) {
                listKey = extractListKey(fieldValue.asText());
            }
        }
        log.info("listkey: {}", listKey);
        return listKey;
    }

    private String extractListKey(String placeholder) {
        log.info("placeholder here: {}", placeholder);
        if (placeholder.contains(".*")) {
            int endIndex = placeholder.indexOf(".*");
            if (endIndex != -1) {
                return placeholder.substring(3, endIndex);
            } else {
                return placeholder.substring(3);
            }
        }
        return null;
    }

    private Optional<ArrayNode> processArrayElementWithListKey(JsonNode arrayElement, String listKey, JsonNode sourceJson) {
        JsonNode listNode = getListNode(sourceJson, listKey);
        log.info("list node: {}",listNode);
        if (listNode != null && listNode.isArray()) {
            ArrayNode processedElements = objectMapper.createArrayNode();
            AtomicInteger finalI = new AtomicInteger(-1);
            listNode.forEach(element -> {
                finalI.set(finalI.get() + 1);
                processArrayElement(arrayElement, sourceJson, listKey, finalI)
                        .ifPresent(processedElements::add);
            });
            return Optional.of(processedElements);
        } else {
            log.error("List node '{}' not found or is not an array in the source JSON.", listKey);
            return Optional.empty();
        }
    }

    private Optional<ObjectNode> processArrayElement(JsonNode arrayElement, JsonNode sourceJson, String listKey, AtomicInteger finalI) {
        ObjectNode processedNode = objectMapper.createObjectNode();

        arrayElement.fields().forEachRemaining(entry -> {
            String elementKey = entry.getKey();
            JsonNode elementValue = entry.getValue();
            String oldValue = listKey + ".*";
            String replacementValue = listKey + "." + finalI;
            log.info("elementg key: {}, element value: {}, oldvalue: {}, new value: {}", elementKey, elementValue, oldValue, replacementValue);

            if (elementValue.isTextual() && isPlaceholder(elementValue.asText())) {
                JsonNode resolvedValue = resolvePlaceholder(
                        elementValue.asText().replace(oldValue, replacementValue),
                        sourceJson
                );
                processedNode.put(elementKey, resolvedValue);
            } else if (elementValue.isArray()) {
                String stringvalue = returnJsonString(elementValue);
                stringvalue = stringvalue.replaceAll(Pattern.quote(oldValue), Matcher.quoteReplacement(replacementValue));
                log.info("string: {}", stringvalue);
                ArrayNode processedArray = handleArrayNode((ArrayNode) returnJsonObject(stringvalue), sourceJson);
                processedNode.set(elementKey, processedArray);
            } else {
                processedNode.set(elementKey, elementValue);
            }
        });

        return Optional.of(processedNode);
    }

    private Optional<JsonNode> processArrayElementWithoutListKey(JsonNode arrayElement, JsonNode sourceJson) {
        ObjectNode processedNode = objectMapper.createObjectNode();

        arrayElement.fields().forEachRemaining(entry -> {
            String elementKey = entry.getKey();
            JsonNode elementValue = entry.getValue();

            if (elementValue.isTextual() && isPlaceholder(elementValue.asText())) {
                JsonNode resolvedValue = resolvePlaceholder(elementValue.asText(), sourceJson);
                processedNode.put(elementKey, resolvedValue);
            } else {
                processedNode.set(elementKey, elementValue);
            }
        });

        return Optional.of(processedNode);
    }

    private JsonNode getListNode(JsonNode sourceJson, String listKey) {
        String[] keys = listKey.split("\\.");
        JsonNode listNode = sourceJson;
        for (String key : keys) {
            if (key.matches("^-?\\d+$")) {
                listNode = getListValue(listNode, key);
            } else {
                listNode = listNode.findPath(key);
            }
        }
        return listNode;
    }


    private boolean isPlaceholder(String text) {
        return text.startsWith("{{") && text.endsWith("}}");
    }

    private JsonNode resolvePlaceholder(String placeholder, JsonNode sourceJson) {
        if (placeholder.contains("*")){
            placeholder = placeholder.replace("*", "0");
        }
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
                return new TextNode(placeholder );            }
        }

        log.info("returning value: {}", currentNode);
        return currentNode;
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
