package com.orbit.research.common;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class Json {
    private final ObjectMapper mapper;
    public Json(ObjectMapper mapper) { this.mapper = mapper; }
    public String write(Object value) { return mapper.writeValueAsString(value); }
    public JsonNode read(String value) { return mapper.readTree(value); }
}
