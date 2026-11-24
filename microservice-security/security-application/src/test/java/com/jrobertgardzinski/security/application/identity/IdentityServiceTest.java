package com.jrobertgardzinski.security.application.identity;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.config.mfa.MfaPolicy;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.FakeEnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.system.identity.DisplayNames;
import com.jrobertgardzinski.security.system.mfa.MfaCompliance;
import com.jrobertgardzinski.security.system.roles.BootstrapAdmins;
import com.jrobertgardzinski.security.system.roles.RequireRole;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The bridge takes strings and answers in plain values; what it decides on its own is how a list
 * of ids is read, how long it may be, and when a source has asked too often.
 */
class IdentityServiceTest {

    private static final String SOURCE = "192.168.0.1";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeUserRepository users = new FakeUserRepository(clock);

    private final IdentityService service = service(120);

    private IdentityService service(int maxPerWindow) {
        return new IdentityService(
                new RequireRole(
                        BootstrapAdmins.none(),
                        email -> users.findBy(email).map(User::roles).orElse(Set.of())),
                new MfaCompliance(new FakeEnrolledFactorRepository(), users, MfaPolicy.withDefaults(), Set.of()),
                users,
                new DisplayNames(users),
                new SourceThrottle(maxPerWindow, Duration.ofMinutes(1), clock));
    }

    private User aUser(String email) {
        return users.save(new User(Email.of(email), new HashedPassword("hash")));
    }

    @Test
    void the_caller_is_described_in_plain_values() {
        User user = aUser("user@example.com");

        IdentityService.Me me = service.me("user@example.com");

        assertEquals(user.id().toString(), me.id());
        assertEquals("user@example.com", me.email());
        assertEquals(List.of(Role.USER.name()), me.roles());
    }

    @Test
    void names_come_back_in_the_order_asked_without_the_ids_nobody_holds() {
        User first = aUser("first@example.com");
        User second = aUser("second@example.com");
        String unknown = UUID.randomUUID().toString();

        IdentityService.DisplayNamesOutcome.Names names = assertInstanceOf(
                IdentityService.DisplayNamesOutcome.Names.class,
                service.displayNames(second.id() + ", " + unknown + ",," + first.id() + "," + second.id(), SOURCE));

        assertEquals(
                List.of(second.id().toString(), first.id().toString()),
                names.users().stream().map(IdentityService.NamedUser::id).toList(),
                "blanks and repeats are dropped, an unknown id is simply absent");
    }

    @Test
    void no_ids_is_an_empty_answer_not_an_error() {
        IdentityService.DisplayNamesOutcome.Names names = assertInstanceOf(
                IdentityService.DisplayNamesOutcome.Names.class,
                service.displayNames("", SOURCE));

        assertEquals(List.of(), names.users());
    }

    @Test
    void something_that_is_not_an_id_is_said_so() {
        assertInstanceOf(
                IdentityService.DisplayNamesOutcome.InvalidId.class,
                service.displayNames("not-an-id", SOURCE));
    }

    @Test
    void more_ids_than_one_question_may_carry_is_refused_with_the_limit() {
        String tooMany = IntStream.rangeClosed(0, IdentityService.MAX_IDS)
                .mapToObj(i -> UUID.randomUUID().toString())
                .collect(Collectors.joining(","));

        IdentityService.DisplayNamesOutcome.TooManyIds refusal = assertInstanceOf(
                IdentityService.DisplayNamesOutcome.TooManyIds.class,
                service.displayNames(tooMany, SOURCE));

        assertEquals(IdentityService.MAX_IDS, refusal.max());
    }

    @Test
    void a_source_that_spent_its_window_is_throttled() {
        IdentityService throttled = service(1);
        throttled.displayNames("", SOURCE);

        assertInstanceOf(
                IdentityService.DisplayNamesOutcome.Throttled.class,
                throttled.displayNames("", SOURCE));
    }
}
