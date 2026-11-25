package com.jrobertgardzinski;

import io.micronaut.http.HttpRequest;

/**
 * The authenticated caller of a protected request: the authorization filter publishes the address
 * under {@link #ATTRIBUTE} once the bearer token is accepted, and every resource reads it here.
 *
 * <p>Used to be called {@code Caller}; renamed when the domain got a {@code Caller} of its own,
 * which the application layer builds from what this hands over.
 */
public final class RequestCaller {

    public static final String ATTRIBUTE = "authenticatedEmail";

    private RequestCaller() {
    }

    /**
     * The caller's address as the filter published it: what a controller hands to the application
     * layer, which builds the domain's {@code Caller} from it.
     *
     * <p>Only meaningful behind the authorization filter; a request that never passed it has no
     * caller — and is answered 401, not 500. {@code GET /me/} (one trailing slash) used to miss the
     * filter's Ant pattern and land here, so a protected route executed unfiltered and answered
     * "No value present" with an Internal Server Error.
     */
    public static String email(HttpRequest<?> request) {
        return request.getAttribute(ATTRIBUTE, String.class)
                .orElseThrow(NotAuthenticatedException::new);
    }

    /** The bearer token as sent, or null when the header is missing or empty. */
    public static String bearerToken(HttpRequest<?> request) {
        return request.getHeaders().getAuthorization()
                .filter(header -> header.startsWith("Bearer "))
                .map(header -> header.substring("Bearer ".length()).trim())
                .filter(token -> !token.isEmpty())
                .orElse(null);
    }
}
