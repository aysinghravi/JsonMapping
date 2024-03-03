package com.poc.transformation.poc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.poc.transformation.poc.model.TestModel;
import reactor.core.publisher.Mono;

public interface MappingService {
    Mono<JsonNode> mapTransform(TestModel testModel);
}
