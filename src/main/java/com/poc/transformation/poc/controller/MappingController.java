package com.poc.transformation.poc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.poc.transformation.poc.model.TestModel;
import com.poc.transformation.poc.service.MappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

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
}
