package com.poc.transformation.poc.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestModel {
    private JsonNode sourceJson;
    private JsonNode destinationJsonTemplate;
}
