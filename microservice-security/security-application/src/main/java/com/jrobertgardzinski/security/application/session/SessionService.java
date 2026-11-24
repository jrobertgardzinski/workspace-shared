package com.jrobertgardzinski.security.application.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.vo.Caller;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.system.session.ListActiveSessions;
import com.jrobertgardzinski.security.system.session.Logout;
import com.jrobertgardzinski.security.system.session.RefreshSession;
import com.jrobertgardzinski.security.system.session.RevokeAllSessions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The bridge for what happens to a session after sign-in: refreshing it, ending it, listing the
 * caller's sessions and ending all of them. Strings in, plain values out.
 */
public final class SessionService {

    private final RefreshSession refreshSession;
    private final Logout logout;
    private final ListActiveSessions listActiveSessions;
    private final RevokeAllSessions revokeAllSessions;
    private final TransactionBoundary transactionBoundary;

    public SessionService(
                    RefreshSession refreshSession,
                    Logout logout,
                    ListActiveSessions listActiveSessions,
                    RevokeAllSessions revokeAllSessions,
                    TransactionBoundary transactionBoundary) {
        this.refreshSession = refreshSession;
        this.logout = logout;
        this.listActiveSessions = listActiveSessions;
        this.revokeAllSessions = revokeAllSessions;
        this.transactionBoundary = transactionBoundary;
    }

    public sealed interface RefreshOutcome {

        record Refreshed(String accessToken, String refreshToken) implements RefreshOutcome {}

        record Expired() implements RefreshOutcome {}

        record NotFound() implements RefreshOutcome {}

        /** A rotated token came back: somebody else holds a copy, so its whole family is revoked. */
        record ReuseDetected() implements RefreshOutcome {}
    }

    /** One session of the caller, as a list shows it. */
    public record Session(UUID family, LocalDateTime expiresAt) {}

    public RefreshOutcome refresh(String refreshToken) {

        SessionRefreshRequest request = new SessionRefreshRequest(
                new RefreshToken(refreshToken));

        RefreshSession.Result result = transactionBoundary.execute(() -> refreshSession.execute(request));

        return switch (result) {
            case RefreshSession.Result.Refreshed refreshed ->
                    new RefreshOutcome.Refreshed(
                            refreshed.sessionTokens().plainAccessToken(),
                            refreshed.sessionTokens().plainRefreshToken());
            case RefreshSession.Result.Expired expired ->
                    new RefreshOutcome.Expired();
            case RefreshSession.Result.NotFound notFound ->
                    new RefreshOutcome.NotFound();
            case RefreshSession.Result.ReuseDetected reuseDetected ->
                    new RefreshOutcome.ReuseDetected();
        };
    }

    public void logout(String refreshToken) {

        transactionBoundary.execute(() -> {
            logout.execute(
                    new RefreshToken(refreshToken));
            return null;
        });
    }

    public List<Session> list(String callerEmail) {

        return listActiveSessions.execute(callerOf(callerEmail)).stream()
                .map(session -> new Session(
                        session.family().value(),
                        session.refreshTokenExpiration().value()))
                .toList();
    }

    public void revokeAll(String callerEmail) {

        Caller caller = callerOf(callerEmail);

        transactionBoundary.execute(() -> {
            revokeAllSessions.execute(caller);
            return null;
        });
    }

    private static Caller callerOf(String callerEmail) {
        return new Caller(
                Email.of(callerEmail));
    }
}
