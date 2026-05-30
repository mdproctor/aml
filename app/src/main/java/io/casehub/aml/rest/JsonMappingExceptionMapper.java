package io.casehub.aml.rest;

import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps {@link JsonMappingException} to HTTP 400 Bad Request.
 *
 * <p>Handles domain validation errors thrown during Jackson deserialization — specifically
 * when a record compact constructor throws {@link IllegalArgumentException} (e.g.
 * {@code SarOutcome.investigationAccuracyScore} out of range). Jackson wraps these as
 * {@code ValueInstantiationException extends JsonMappingException}, so an
 * {@code ExceptionMapper<IllegalArgumentException>} alone cannot catch them.
 *
 * <p>When the cause is {@link IllegalArgumentException}, the domain error message is
 * surfaced in the response body. For other mapping failures (unknown field, type mismatch),
 * a generic 400 is returned without exposing Jackson internals.
 */
@Provider
@ApplicationScoped
public class JsonMappingExceptionMapper implements ExceptionMapper<JsonMappingException> {

    @Override
    public Response toResponse(final JsonMappingException exception) {
        final Throwable cause = exception.getCause();
        if (cause instanceof IllegalArgumentException iae) {
            final String message = iae.getMessage() != null ? iae.getMessage() : "Bad request";
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse(message))
                    .build();
        }
        return Response.status(Response.Status.BAD_REQUEST).build();
    }
}
