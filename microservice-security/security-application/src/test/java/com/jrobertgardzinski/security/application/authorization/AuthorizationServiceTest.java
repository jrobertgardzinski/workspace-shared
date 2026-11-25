package com.jrobertgardzinski.security.application.authorization;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.config.mfa.MfaPolicy;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.port.AccessTokenMint;
import com.jrobertgardzinski.security.domain.repository.FakeEnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FakeSessionRepository;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.system.authorization.Authorize;
import com.jrobertgardzinski.security.system.mfa.MfaCompliance;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge takes the bearer token as a string and answers with an address or with nothing; what
 * it decides on its own is that a token which cannot even be constructed is simply not a session.
 */
class AuthorizationServiceTest {

    private static final String OWNER = "owner@example.com";
    private static final String PASSWORD = "StrongPassword1!";
    private static final int ACCESS_TOKEN_HOURS = 1;

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeUserRepository users = new FakeUserRepository(clock);
    private final FakeSessionRepository sessions = new FakeSessionRepository(clock);
    private final FakeEnrolledFactorRepository enrolledFactors = new FakeEnrolledFactorRepository();
    private final FakeHashAlgorithm hashAlgorithm = new FakeHashAlgorithm();

    private final AuthorizationService service = new AuthorizationService(
            new Authorize(sessions, clock),
            users,
            new MfaCompliance(enrolledFactors, users, MfaPolicy.withDefaults(), Set.of()));

    private void theOwnerExists() {
        users.save(new User(Email.of(OWNER), hashAlgorithm.hash(PlaintextPassword.of(PASSWORD))));
    }

    /** A session of the owner's, as a sign-in leaves it: its access token as the client holds it. */
    private String theOwnersAccessToken() {
        return sessions.create(
                SessionTokens.createFor(
                        Email.of(OWNER),
                        new SessionTokensConfig(
                                new RefreshTokenValidityInHours(24),
                                new AccessTokenValidityInHours(ACCESS_TOKEN_HOURS)),
                        clock,
                        AccessTokenMint.RANDOM),
                SessionFamily.start()).plainAccessToken();
    }

    @Test
    void a_live_token_names_the_address_the_request_acts_as() {
        theOwnerExists();

        AuthorizationService.Outcome.Authorized authorized = assertInstanceOf(
                AuthorizationService.Outcome.Authorized.class,
                service.authorize(theOwnersAccessToken()));

        assertEquals(OWNER, authorized.email());
    }

    @Test
    void a_missing_a_blank_and_an_unknown_token_are_one_answer() {
        for (String token : new String[] {null, "   ", "a-token-nobody-was-issued"}) {
            assertInstanceOf(
                    AuthorizationService.Outcome.NotAuthenticated.class,
                    service.authorize(token));
        }
    }

    @Test
    void an_expired_token_is_no_session_either() {
        theOwnerExists();
        String token = theOwnersAccessToken();

        clock.advance(Duration.ofHours(ACCESS_TOKEN_HOURS).plusMinutes(1));

        assertInstanceOf(
                AuthorizationService.Outcome.NotAuthenticated.class,
                service.authorize(token));
    }

    @Test
    void an_account_under_the_factor_floor_of_its_roles_does_not_meet_it_until_it_enrols() {
        theOwnerExists();
        assertTrue(service.meetsMfaFloor(OWNER), "a USER owes the password alone");

        // a MODERATOR owes two factors: the password and one enrolled
        users.setRoles(Email.of(OWNER), Set.of(Role.MODERATOR));
        assertFalse(service.meetsMfaFloor(OWNER));

        enrolledFactors.enrol(new EnrolledFactor(Email.of(OWNER), FactorType.EMAIL_CODE, "E-mail code", 0, OWNER));
        assertTrue(service.meetsMfaFloor(OWNER));
    }
}
