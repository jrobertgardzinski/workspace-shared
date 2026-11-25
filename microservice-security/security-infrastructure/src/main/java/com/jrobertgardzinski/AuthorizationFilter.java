package com.jrobertgardzinski;


import com.jrobertgardzinski.security.application.authorization.AuthorizationService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;

/**
 * Guards protected resources. Reads the {@code Authorization: Bearer <accessToken>} header, asks
 * {@link AuthorizationService} whose session it is, and either lets the request through (publishing
 * the authenticated email under {@link RequestCaller#ATTRIBUTE} for the resource to read) or rejects
 * it with 401 — for a missing, malformed, unknown or expired token alike.
 *
 * <p>It also enforces the MFA role floor: a caller whose roles demand more factors than they have
 * is let through only to the enrolment endpoints and {@code /me} — everything else answers 403
 * {@code MFA_ENROLMENT_REQUIRED} until they comply. The session is real; it is just boxed to
 * becoming compliant. Whether the floor is met is the service's answer; which doors stay open
 * under it is decided here, because doors are paths.
 */
// "/me" alone does not match "/me/" — the router strips the trailing slash on the way to the
// controller, but the filter's pattern is matched before that, so GET /me/ ran unfiltered
@ServerFilter({"/me", "/me/**", "/sessions", "/sessions/**", "/account/**", "/admin/**"})
final class AuthorizationFilter {

    private final AuthorizationService authorizationService;

    AuthorizationFilter(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @RequestFilter
    @Nullable
    HttpResponse<?> authorize(HttpRequest<?> request) {
        if (!(authorizationService.authorize(RequestCaller.bearerToken(request))
                instanceof AuthorizationService.Outcome.Authorized authorized)) {
            return HttpResponse.unauthorized().body(Refusal.of("NOT_AUTHENTICATED"));
        }
        request.setAttribute(RequestCaller.ATTRIBUTE, authorized.email());
        if (!enrolmentExempt(request.getPath()) && !authorizationService.meetsMfaFloor(authorized.email())) {
            return HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN)
                    .body(Refusal.alsoAsError("MFA_ENROLMENT_REQUIRED"));
        }
        return null; // proceed to the resource
    }

    /**
     * /me, the factor-enrolment endpoints and step-up stay open so an under-enrolled user can become
     * compliant — enrolling a factor now requires stepping up first, so the step-up endpoint must be
     * reachable under the floor too, or the user would be boxed out of the very act that frees them.
     */
    private static boolean enrolmentExempt(String path) {
        return path.equals("/me") || path.startsWith("/account/factors")
                || path.startsWith("/account/step-up");
    }
}
