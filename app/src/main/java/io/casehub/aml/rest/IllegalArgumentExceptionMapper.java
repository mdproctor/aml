package io.casehub.aml.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps {@link IllegalArgumentException} thrown from service methods to HTTP 400 Bad Request.
 *
 * <p>Covers the service-layer path only. For domain validation errors thrown during
 * Jackson deserialization (compact constructor violations), see {@link JsonMappingExceptionMapper}.
 */
@Provider
@ApplicationScoped
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(final IllegalArgumentException exception) {
        final String message = exception.getMessage() != null ? exception.getMessage() : "Bad request";
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(message))
                .build();
    }
}
