package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.Caller;
import io.micronaut.http.HttpRequest;

/**
 * The authenticated caller of a protected request: the authorization filter publishes the address
 * under {@link #ATTRIBUTE} once the bearer token is accepted, and every resource reads it here.
 *
 * <p>Used to be called {@code Caller}; renamed when the domain got a {@link Caller} of its own,
 * because the controllers live in this very package and the import would have shadowed the helper.
 */
public final class RequestCaller {

    public static final String ATTRIBUTE = "authenticatedEmail";

    private RequestCaller() {
    }

    /**
     * Only meaningful behind the authorization filter; a request that never passed it has no
     * caller — and is answered 401, not 500. {@code GET /me/} (one trailing slash) used to miss the
     * filter's Ant pattern and land here, so a protected route executed unfiltered and answered
     * "No value present" with an Internal Server Error.
     */
    public static Caller of(HttpRequest<?> request) {
        return new Caller(Email.of(request.getAttribute(ATTRIBUTE, String.class)
                .orElseThrow(NotAuthenticatedException::new)));
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
