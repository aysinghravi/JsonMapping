package com.poc.transformation.poc.service;

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
//        return Mono.just((JsonNode)handleArrayNode((ArrayNode) testModel.getDestinationJsonTemplate().get("pdetails"), testModel.getSourceJson()));
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
        log.info("handling array nodes");
        ArrayNode resultArray = objectMapper.createArrayNode();

        destinationNode.forEach(arrayElement -> {
            String listKey = null;

            // Iterate over all fields of the array node element to find the list key
            Iterator<Map.Entry<String, JsonNode>> fieldsIterator = arrayElement.fields();
            while (fieldsIterator.hasNext()) {
                Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
                JsonNode fieldValue = fieldEntry.getValue();
                if (fieldValue.isTextual() && isPlaceholder(fieldValue.asText())) {
                    String placeholder = fieldValue.asText();
                    int startIndex = placeholder.indexOf("{{$");
                    int endIndex = placeholder.indexOf(".*");
                    if (startIndex != -1 && endIndex != -1) {
                        listKey = placeholder.substring(startIndex + 3, endIndex);
                        break; // Exit loop if list key is found
                    }
                }
            }

            if (listKey == null) {
                log.error("List key not found in array node entry: {}", arrayElement);
                return; // Skip processing this array node entry if list key is not found
            }

            JsonNode listNode = sourceJson.get(listKey); // Fetching the list node from source JSON

            if (listNode == null || !listNode.isArray()) {
                log.error("List node '{}' not found or is not an array in the source JSON.", listKey);
                return; // Skip processing this array node entry if list node not found or is not an array
            }

            int listSize = listNode.size();
            for (int i = 0; i < listSize; i++) {
                ObjectNode processedNode = objectMapper.createObjectNode();
                int finalI = i;
                arrayElement.fields().forEachRemaining(elementEntry -> {
                    String elementKey = elementEntry.getKey();
                    JsonNode elementValue = elementEntry.getValue();
                    log.info("element value: {}", elementValue);
                    if (elementValue.isTextual() && isPlaceholder(elementValue.asText())) {
                        String resolvedValue = resolvePlaceholder(elementValue.asText().replace("*", String.valueOf(finalI)), sourceJson);
                       log.info("resolved value: {}", resolvedValue);
                        processedNode.put(elementKey, resolvedValue);
                    } else {
                        processedNode.set(elementKey, elementValue);
                    }
                });
                resultArray.add(processedNode);
            }
        });

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
                return placeholder;
            }
        }

        return currentNode.asText();
    }

    private JsonNode getListValue(JsonNode payloadJson, String placeholder){
        log.debug("fetching list/array values");
        final int index = Integer.parseInt(placeholder);
        if (payloadJson.has(index)) {
            return payloadJson.get(index);
        } else {
            return new TextNode("{{" + placeholder + "}}");
        }
    }
}
