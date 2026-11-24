package com.jrobertgardzinski.security.application.registration;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.email.config.EmailPolicy;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.AdmitEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.application.BrokenRule;
import com.jrobertgardzinski.security.application.PassThroughTransactionBoundary;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.system.registration.Register;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import com.jrobertgardzinski.security.system.verification.RequestEmailVerification;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge takes strings and answers in plain values; what it decides on its own is who gets
 * which mail, and when a source has asked too often.
 */
class RegistrationServiceTest {

    private static final String SOURCE = "192.168.0.1";
    private static final String STRONG_PASSWORD = "StrongPassword1!";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeUserRepository users = new FakeUserRepository(clock);
    private final List<String> verificationLinks = new ArrayList<>();
    private final List<String> alreadyRegisteredNotices = new ArrayList<>();

    private final RegistrationService service = service(5);

    private RegistrationService service(int maxPerWindow) {
        return new RegistrationService(
                new Register(
                        users,
                        new AdmitEmail(new EmailPolicy()),
                        new CreatePasswordHash(new FakeHashAlgorithm(), PasswordPolicy::withDefaults)),
                new RequestEmailVerification(
                        users,
                        (email, token) -> verificationLinks.add(email.value())),
                users,
                email -> alreadyRegisteredNotices.add(email.value()),
                new EmailPolicy(),
                new SourceThrottle(maxPerWindow, Duration.ofMinutes(15), clock),
                new PassThroughTransactionBoundary());
    }

    @Test
    void a_fresh_address_is_registered_and_mailed_a_verification_link() {
        RegistrationService.Outcome outcome = service.register("user@example.com", STRONG_PASSWORD, SOURCE);

        assertInstanceOf(RegistrationService.Outcome.Registered.class, outcome);
        assertTrue(users.findBy(Email.of("user@example.com")).isPresent());
        assertEquals(List.of("user@example.com"), verificationLinks);
        assertTrue(alreadyRegisteredNotices.isEmpty());
    }

    @Test
    void a_taken_and_verified_address_gets_the_notice_and_no_link() {
        Email taken = Email.of("user@example.com");
        users.save(new User(taken, new HashedPassword("seed-hash")));
        users.markVerified(taken);

        RegistrationService.Outcome outcome = service.register("user@example.com", STRONG_PASSWORD, SOURCE);

        assertInstanceOf(RegistrationService.Outcome.EmailAlreadyTaken.class, outcome);
        assertEquals(List.of("user@example.com"), alreadyRegisteredNotices);
        assertTrue(verificationLinks.isEmpty());
    }

    @Test
    void a_taken_but_unverified_address_gets_a_fresh_link_and_no_notice() {
        users.save(new User(Email.of("user@example.com"), new HashedPassword("seed-hash")));

        RegistrationService.Outcome outcome = service.register("user@example.com", STRONG_PASSWORD, SOURCE);

        assertInstanceOf(RegistrationService.Outcome.EmailAlreadyTaken.class, outcome);
        assertEquals(List.of("user@example.com"), verificationLinks);
        assertTrue(alreadyRegisteredNotices.isEmpty());
    }

    @Test
    void strings_the_domain_cannot_construct_come_back_as_broken_rules_of_both_channels() {
        RegistrationService.Outcome outcome = service.register("not-an-address", "weak", SOURCE);

        RegistrationService.Outcome.Rejected rejected =
                assertInstanceOf(RegistrationService.Outcome.Rejected.class, outcome);
        assertFalse(rejected.emailErrors().isEmpty());
        assertTrue(
                rejected.passwordErrors().contains(
                        new BrokenRule("MIN_LENGTH_NOT_MET", PasswordPolicy.withDefaults().minLength().value())),
                "the broken rule carries the parameter in force: " + rejected.passwordErrors());
        assertTrue(verificationLinks.isEmpty());
    }

    @Test
    void a_missing_address_and_password_are_broken_rules_not_exceptions() {
        RegistrationService.Outcome outcome = service.register(null, null, SOURCE);

        RegistrationService.Outcome.Rejected rejected =
                assertInstanceOf(RegistrationService.Outcome.Rejected.class, outcome);
        assertFalse(rejected.emailErrors().isEmpty());
        assertFalse(rejected.passwordErrors().isEmpty());
    }

    @Test
    void a_channel_that_passed_is_an_empty_list() {
        RegistrationService.Outcome outcome = service.register("user@example.com", "weak", SOURCE);

        RegistrationService.Outcome.Rejected rejected =
                assertInstanceOf(RegistrationService.Outcome.Rejected.class, outcome);
        assertTrue(rejected.emailErrors().isEmpty());
        assertFalse(rejected.passwordErrors().isEmpty());
    }

    @Test
    void a_source_that_spent_its_window_is_throttled_before_anything_is_judged() {
        RegistrationService throttled = service(1);
        throttled.register("first@example.com", STRONG_PASSWORD, SOURCE);

        RegistrationService.Outcome outcome = throttled.register("second@example.com", STRONG_PASSWORD, SOURCE);

        RegistrationService.Outcome.Throttled refusal =
                assertInstanceOf(RegistrationService.Outcome.Throttled.class, outcome);
        assertTrue(refusal.retryAfterSeconds() > 0);
        assertTrue(users.findBy(Email.of("second@example.com")).isEmpty());
    }

    @Test
    void another_source_has_a_window_of_its_own() {
        RegistrationService throttled = service(1);
        throttled.register("first@example.com", STRONG_PASSWORD, SOURCE);

        RegistrationService.Outcome outcome = throttled.register("second@example.com", STRONG_PASSWORD, "192.168.0.2");

        assertInstanceOf(RegistrationService.Outcome.Registered.class, outcome);
    }
}
