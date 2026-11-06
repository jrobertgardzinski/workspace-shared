package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.port.AccessTokenMint;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.vo.StoredSession;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

public class RefreshSession {

    private final SessionRepository sessionRepository;
    private final Clock clock;
    private final SessionTokensConfig config;
    private final AccessTokenMint accessTokenMint;
    private final Duration maxSessionLifetime;

    public RefreshSession(
                    SessionRepository sessionRepository,
                    Clock clock,
                    SessionTokensConfig config,
                    AccessTokenMint accessTokenMint,
                    Duration maxSessionLifetime) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
        this.config = config;
        this.accessTokenMint = accessTokenMint;
        this.maxSessionLifetime = maxSessionLifetime;
    }

    public sealed interface Result {

        /** A new session was issued; the old refresh token has been rotated out. */
        record Refreshed(SessionTokens sessionTokens) implements Result {}

        /** The session was found but its refresh token had expired. */
        record Expired(Email email) implements Result {}

        /** No session matched the presented refresh token. */
        record NotFound() implements Result {}

        /** An already-rotated refresh token was replayed — theft. The whole family has been revoked. */
        record ReuseDetected() implements Result {}
    }

    public Result execute(SessionRefreshRequest request) {

        RefreshToken refreshToken = request.refreshToken();

        Optional<StoredSession> storedSession = sessionRepository.findByRefreshToken(refreshToken);

        if (
                storedSession.isEmpty()
        ) {
            return new Result.NotFound();
        }

        StoredSession session = storedSession.get();

        switch (session.status()) {
            case ROTATED -> {
                // this refresh token was already rotated away — a replay signals theft
                sessionRepository.revokeFamily(session.family());
                return new Result.ReuseDetected();
            }
            case ACTIVE -> {}
        }

        if (
                session.refreshTokenExpiration().hasExpired(clock)
        ) {
            return new Result.Expired(
                    session.email());
        }

        // The absolute ceiling. The expiry above says how long this session may sit IDLE, and
        // rotation hands out a fresh one every time — so without this, a session touched once a day
        // lived for ever and "signed in since last March" was a state nothing reconsidered. Measured
        // from when the LINEAGE started, which is the one date a refresh cannot move.
        if (
                isPastItsWholeLife(session)
        ) {
            // the family goes with it: leaving the rotated rows behind would let the presented token
            // be replayed into a theft report for a session that simply grew old
            sessionRepository.revokeFamily(session.family());
            return new Result.Expired(
                    session.email());
        }

        // Single-use: rotate the presented token out, issue a new one in the same family.
        //
        // The rotation is CONDITIONAL and its result decides the branch. Reading the status above
        // and writing unconditionally here was a check-then-act: two truly simultaneous refreshes
        // both saw ACTIVE, both wrote ROTATED and both minted a successor, so one session forked
        // into two live chains — and a thief refreshing alongside the victim walked away with a
        // lineage nothing would ever flag.
        //
        // Losing the race is therefore treated exactly like presenting an already-rotated token,
        // because from here the two are indistinguishable. The honest cost: two browser tabs
        // refreshing at once now revoke the family and sign the user out, where before one of them
        // silently won. That is the conservative side of the trade and the one OAuth's own guidance
        // takes — a benign double refresh costs a sign-in, an undetected stolen token costs the
        // account. Softening it needs a grace window with a replayable successor, which is a schema
        // change and a separate decision.
        //
        // Rotation and creation go to the repository as ONE call, because they have to be one step.
        // As two, a concurrent revokeFamily — the very thing the branch below performs — could land
        // between them, destroying the lineage and then having a successor written into it: a live
        // session inside a family the service reports as revoked.
        Optional<SessionTokens> sessionTokens = sessionRepository.rotateAndCreate(
                refreshToken,
                () -> SessionTokens.createFor(session.email(), config, clock, accessTokenMint),
                session.family());

        if (
                sessionTokens.isEmpty()
        ) {
            sessionRepository.revokeFamily(session.family());
            return new Result.ReuseDetected();
        }

        return new Result.Refreshed(
                sessionTokens.get());
    }

    private boolean isPastItsWholeLife(StoredSession session) {
        return session.familyStartedAt().plus(maxSessionLifetime)
                .isBefore(LocalDateTime.now(clock));
    }
}
