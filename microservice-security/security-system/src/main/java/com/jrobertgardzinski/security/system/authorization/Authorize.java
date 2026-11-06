package com.jrobertgardzinski.security.system.authorization;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.vo.AccessGrant;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;

import java.time.Clock;
import java.util.Optional;

/**
 * Authorizes a request by its access token: the token names a session, which must exist and not be
 * expired. An opaque token validated by lookup (not a self-contained JWT), so a session can be
 * revoked — once its row is gone (e.g. rotated away on refresh), the access token stops working.
 */
public class Authorize {

    private final SessionRepository sessionRepository;
    private final Clock clock;

    public Authorize(
                    SessionRepository sessionRepository,
                    Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    public sealed interface Result {

        /** The access token is valid; the request acts as this user. */
        record Authorized(Email email) implements Result {}

        /** No valid, unexpired session matched the presented access token. */
        record Unauthorized() implements Result {}
    }

    public Result execute(AccessToken accessToken) {

        Optional<AccessGrant> accessGrant = sessionRepository.findByAccessToken(accessToken);

        if (
                accessGrant.isEmpty() ||
                accessGrant.get().expiration().hasExpired(clock)
        ) {
            return new Result.Unauthorized();
        }

        return new Result.Authorized(
                accessGrant.get().email());
    }
}
