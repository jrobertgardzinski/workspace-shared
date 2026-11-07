package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.AttemptedAccount;
import com.jrobertgardzinski.security.domain.vo.LockoutSubject;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.system.mfa.PendingAuthentication;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Epic("Use case")
@Feature("Authentication")
@Story("Update brute-force records")
class _UpdateBruteForceRecordsTest {

    private static final Source IP = Source.of(new IpAddress("192.168.0.1"));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private RejectedAuthenticationRepository rejectedAuthenticationRepository;
    private _UpdateBruteForceRecords updateBruteForceRecords;

    @BeforeTry
    void init() {
        rejectedAuthenticationRepository = Mockito.mock(RejectedAuthenticationRepository.class);
        updateBruteForceRecords = new _UpdateBruteForceRecords(rejectedAuthenticationRepository, CLOCK);
    }

    @Example
    @Label("Records a failed authentication for the IP stamped with the current time")
    void records_failed_authentication() {
        LockoutSubject subject = new LockoutSubject(IP, AttemptedAccount.of(Email.of("victim@example.com")));

        updateBruteForceRecords.execute(subject);

        Mockito.verify(rejectedAuthenticationRepository)
                .create(new RejectedAuthenticationDetails(subject, LocalDateTime.now(CLOCK)));
    }

    @Example
    @Label("A wrong proof in a sign-in chain is charged to the pair the chain began from")
    void records_a_wrong_proof_against_the_chains_pair() {
        Email victim = Email.of("victim@example.com");
        LockoutSubject subject = new LockoutSubject(IP, AttemptedAccount.of(victim));

        updateBruteForceRecords.execute(chainOf(victim, Optional.of(IP)));

        Mockito.verify(rejectedAuthenticationRepository)
                .create(new RejectedAuthenticationDetails(subject, LocalDateTime.now(CLOCK)));
    }

    @Example
    @Label("A step-up chain has no sign-in behind it, so nothing is written down")
    void a_step_up_chain_writes_nothing_down() {
        updateBruteForceRecords.execute(chainOf(Email.of("victim@example.com"), Optional.empty()));

        Mockito.verifyNoInteractions(rejectedAuthenticationRepository);
    }

    private static PendingAuthentication chainOf(Email email, Optional<Source> source) {
        return new PendingAuthentication(email, source, List.of(), null, 3, LocalDateTime.now(CLOCK));
    }
}
