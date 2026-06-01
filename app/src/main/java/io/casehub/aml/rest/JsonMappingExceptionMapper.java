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
 * <p>Handles domain validation errors that reach resource methods wrapped in
 * {@code JsonMappingException} — for example, from Jackson filters or interceptors.
 * Note: deserialization errors thrown directly by message body readers are caught by
 * the JAX-RS runtime before exception mappers run (JAX-RS spec §4.2.4); those must
 * be handled by moving validation into the resource method body instead.
 *
 * <p>When the cause is {@link IllegalArgumentException}, the domain error message is
 * surfaced in the response body. For other mapping failures, a generic 400 is returned.
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
