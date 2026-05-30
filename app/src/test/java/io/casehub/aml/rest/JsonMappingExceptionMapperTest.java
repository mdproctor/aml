package io.casehub.aml.rest;

import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.type.SimpleType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonMappingExceptionMapperTest {

    private final JsonMappingExceptionMapper mapper = new JsonMappingExceptionMapper();

    @Test
    void when_cause_is_illegal_argument_returns_400_with_message() {
        final String message = "investigationAccuracyScore must be in [0.0, 1.0], got: 1.5";
        final ValueInstantiationException wrapped = ValueInstantiationException.from(
                null, message, SimpleType.constructUnsafe(Object.class),
                new IllegalArgumentException(message));

        final Response response = mapper.toResponse(wrapped);

        assertEquals(400, response.getStatus());
        final ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals(message, body.error());
    }

    @Test
    void when_cause_is_not_illegal_argument_returns_400_with_no_body() {
        final ValueInstantiationException wrapped = ValueInstantiationException.from(
                null, "type mismatch", SimpleType.constructUnsafe(Object.class),
                new RuntimeException("unexpected"));

        final Response response = mapper.toResponse(wrapped);

        assertEquals(400, response.getStatus());
        assertNull(response.getEntity(), "non-IllegalArgumentException causes should not expose internals");
    }

    @Test
    void when_cause_illegal_argument_has_null_message_returns_fallback() {
        final ValueInstantiationException wrapped = ValueInstantiationException.from(
                null, "null message", SimpleType.constructUnsafe(Object.class),
                new IllegalArgumentException((String) null));

        final Response response = mapper.toResponse(wrapped);

        assertEquals(400, response.getStatus());
        final ErrorResponse body = (ErrorResponse) response.getEntity();
        assertNotNull(body.error(), "error field must not be null even when cause message is null");
    }
}
