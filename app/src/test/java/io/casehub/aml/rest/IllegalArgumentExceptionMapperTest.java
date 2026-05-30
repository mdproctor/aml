package io.casehub.aml.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IllegalArgumentExceptionMapperTest {

    private final IllegalArgumentExceptionMapper mapper = new IllegalArgumentExceptionMapper();

    @Test
    void maps_to_status_400() {
        final Response response = mapper.toResponse(
                new IllegalArgumentException("investigationAccuracyScore must be in [0.0, 1.0], got: 1.5"));
        assertEquals(400, response.getStatus());
    }

    @Test
    void response_body_contains_the_exception_message() {
        final String message = "investigationAccuracyScore must be in [0.0, 1.0], got: 1.5";
        final Response response = mapper.toResponse(new IllegalArgumentException(message));
        final ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals(message, body.error());
    }

    @Test
    void null_message_returns_fallback_not_null_in_body() {
        final Response response = mapper.toResponse(new IllegalArgumentException((String) null));
        assertEquals(400, response.getStatus());
        final ErrorResponse body = (ErrorResponse) response.getEntity();
        assertNotNull(body.error(), "error field must not be null");
    }
}
