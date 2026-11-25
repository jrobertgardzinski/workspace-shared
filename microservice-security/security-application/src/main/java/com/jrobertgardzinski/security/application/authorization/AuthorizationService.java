package com.jrobertgardzinski.security.application.authorization;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.system.authorization.Authorize;
import com.jrobertgardzinski.security.system.mfa.MfaCompliance;

import java.util.Set;

/**
 * The bridge for the question in front of every protected resource: whose request is this, and
 * may that person go everywhere yet. Strings in, plain values out.
 */
public final class AuthorizationService {

    private final Authorize authorize;
    private final UserRepository userRepository;
    private final MfaCompliance mfaCompliance;

    public AuthorizationService(
                    Authorize authorize,
                    UserRepository userRepository,
                    MfaCompliance mfaCompliance) {
        this.authorize = authorize;
        this.userRepository = userRepository;
        this.mfaCompliance = mfaCompliance;
    }

    public sealed interface Outcome {

        /** The token names a live session; the request acts as this address. */
        record Authorized(String email) implements Outcome {}

        /** Missing, blank, unknown or expired: one answer, so a refusal says nothing about why. */
        record NotAuthenticated() implements Outcome {}
    }

    /** {@code accessToken} is the bearer token as sent, or null when there was none. */
    public Outcome authorize(String accessToken) {

        AccessToken token;
        try {
            token = new AccessToken(accessToken);
        } catch (IllegalArgumentException missingOrBlank) {
            return new Outcome.NotAuthenticated();
        }

        Authorize.Result result = authorize.execute(token);

        return switch (result) {
            case Authorize.Result.Authorized authorized ->
                    new Outcome.Authorized(
                            authorized.email().value());
            case Authorize.Result.Unauthorized unauthorized ->
                    new Outcome.NotAuthenticated();
        };
    }

    /**
     * Whether the account carries as many factors as its roles demand. An account under its floor
     * has a real session, boxed to becoming compliant: which doors stay open to it is the edge's
     * to say, because doors are paths.
     */
    public boolean meetsMfaFloor(String email) {

        Email address = Email.of(email);

        Set<Role> roles = userRepository.findBy(address)
                .map(User::roles)
                .orElse(Set.of(Role.USER));

        return mfaCompliance.isCompliant(address, roles);
    }
}
