package com.carsocialmedia.backend.shared.persistence;

import org.hibernate.type.format.AbstractJsonFormatMapper;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;

/**
 * Maps {@code jsonb} columns (e.g. {@code notifications.payload}) with Jackson 3.
 *
 * <p>Hibernate can discover a JSON mapper on its own, but only a Jackson 2 one
 * ({@code com.fasterxml.jackson.databind}). Spring Boot 4 ships Jackson 3
 * ({@code tools.jackson.databind}), so that discovery finds nothing and any JSON column blows up at
 * flush time with "Could not find a FormatMapper for the JSON format". This bridges the gap using
 * the very {@code ObjectMapper} Spring already configures for the REST layer, so a payload
 * serializes the same way whether it is on its way to Postgres or to a client.
 *
 * <p>{@link AbstractJsonFormatMapper} handles the {@code String}/{@code Object} passthrough cases;
 * only the real (de)serialization is left to us.
 */
public class Jackson3JsonFormatMapper extends AbstractJsonFormatMapper {

    private final ObjectMapper objectMapper;

    public Jackson3JsonFormatMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected <T> T fromString(CharSequence charSequence, Type type) {
        return objectMapper.readValue(charSequence.toString(), objectMapper.constructType(type));
    }

    @Override
    protected <T> String toString(T value, Type type) {
        return objectMapper.writeValueAsString(value);
    }
}
