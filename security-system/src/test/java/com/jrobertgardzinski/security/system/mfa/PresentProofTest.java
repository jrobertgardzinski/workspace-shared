package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.port.CodeChannel;
import com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.ProofStatus;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One proof against one ticket, in the one place both a sign-in and a step-up now go through.
 *
 * <p>The two used to carry their own copy of this protocol, and the copies drifted: the step-up
 * once trusted its ticket for ever while the sign-in checked its age. What is pinned here is the
 * protocol itself: an old ticket ends, a wrong proof costs a try, the last try costs the ticket,
 * and a right proof either finishes the chain or moves it to the next factor.
 */
@Epic("Multi-factor sign-in")
@Feature("Presenting a proof")
class PresentProofTest {

    private static final Email USER = Email.of("proof@example.com");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final int TICKET_TTL_MINUTES = 10;
    private static final ChallengeCodeConfig CONFIG = ChallengeCodeConfig.withDefaults();

    private final RememberedCodes mailbox = new RememberedCodes(FactorType.EMAIL_CODE);
    private final RememberedCodes phone = new RememberedCodes(FactorType.SMS_CODE);
    private final MfaChain mfaChain = new MfaChain(
            new FactorRegistry(List.of(
                    new CodeFactor(mailbox, raw -> "hash:" + raw, CONFIG, CLOCK),
                    new CodeFactor(phone, raw -> "hash:" + raw, CONFIG, CLOCK))),
            CONFIG, Mockito.mock(RecoveryCodeRepository.class), raw -> "hash:" + raw, CLOCK, TICKET_TTL_MINUTES);
    private final FakePendingAuthenticationStore store = new FakePendingAuthenticationStore();
    private final PresentProof presentProof = new PresentProof(mfaChain, store, CLOCK);

    private static final EnrolledFactor EMAIL_FACTOR = new EnrolledFactor(
            USER, FactorType.EMAIL_CODE, FactorLabels.of(FactorType.EMAIL_CODE), 0, USER.value());
    private static final EnrolledFactor SMS_FACTOR = new EnrolledFactor(
            USER, FactorType.SMS_CODE, FactorLabels.of(FactorType.SMS_CODE), 1, "+48111222333");

    @Test
    @DisplayName("a ticket nobody opened is invalid")
    void an_unknown_ticket_is_invalid() {
        PresentProof.Result result = presentProof.present("a-ticket-nobody-opened", "000000");

        assertThat(result.status()).isEqualTo(ProofStatus.INVALID_TICKET);
        assertThat(result.chain()).isNull();
    }

    @Test
    @DisplayName("a ticket past its window is invalid and gone, whoever holds the right code")
    void an_aged_ticket_is_invalid_and_closed() {
        String ticket = store.open(mfaChain.begin(USER, List.of(EMAIL_FACTOR)));
        Clock later = Clock.offset(CLOCK, Duration.ofMinutes(TICKET_TTL_MINUTES + 1));

        PresentProof.Result result = new PresentProof(mfaChain, store, later).present(ticket, mailbox.lastCode);

        assertThat(result.status()).isEqualTo(ProofStatus.INVALID_TICKET);
        assertThat(store.find(ticket)).isEmpty();
    }

    @Test
    @DisplayName("a wrong proof costs one try and leaves the ticket open")
    void a_wrong_proof_costs_a_try() {
        String ticket = store.open(mfaChain.begin(USER, List.of(EMAIL_FACTOR)));

        PresentProof.Result result = presentProof.present(ticket, "000000");

        assertThat(result.status()).isEqualTo(ProofStatus.WRONG_PROOF);
        assertThat(result.chain().attemptsLeft()).isEqualTo(CONFIG.maxAttempts().value() - 1);
        assertThat(store.find(ticket)).isPresent();
    }

    @Test
    @DisplayName("the last wrong proof costs the ticket, and the right code no longer finds it")
    void the_last_try_costs_the_ticket() {
        String ticket = store.open(mfaChain.begin(USER, List.of(EMAIL_FACTOR)));

        PresentProof.Result result = null;
        for (int attempt = 1; attempt <= CONFIG.maxAttempts().value(); attempt++) {
            result = presentProof.present(ticket, "000000");
        }

        assertThat(result.status()).isEqualTo(ProofStatus.TOO_MANY_ATTEMPTS);
        assertThat(result.chain())
                .as("the chain still comes back, so a sign-in knows whom to charge the miss to")
                .isNotNull();
        assertThat(store.find(ticket)).isEmpty();
        assertThat(presentProof.present(ticket, mailbox.lastCode).status())
                .isEqualTo(ProofStatus.INVALID_TICKET);
    }

    @Test
    @DisplayName("the right proof for the last factor completes the chain and closes the ticket")
    void the_right_proof_completes_a_one_factor_chain() {
        String ticket = store.open(mfaChain.begin(USER, List.of(EMAIL_FACTOR)));

        PresentProof.Result result = presentProof.present(ticket, mailbox.lastCode);

        assertThat(result.status()).isEqualTo(ProofStatus.PASSED);
        assertThat(result.chainCompleted()).isTrue();
        assertThat(store.find(ticket)).isEmpty();
    }

    @Test
    @DisplayName("the right proof with a factor still owed moves the chain on and keeps the ticket")
    void the_right_proof_advances_a_longer_chain() {
        String ticket = store.open(mfaChain.begin(USER, List.of(EMAIL_FACTOR, SMS_FACTOR)));

        PresentProof.Result result = presentProof.present(ticket, mailbox.lastCode);

        assertThat(result.status()).isEqualTo(ProofStatus.PASSED);
        assertThat(result.chainCompleted()).isFalse();
        assertThat(result.chain().currentType()).isEqualTo(FactorType.SMS_CODE);
        assertThat(result.chain().attemptsLeft())
                .as("a new factor starts with a full set of tries")
                .isEqualTo(CONFIG.maxAttempts().value());
        assertThat(store.find(ticket)).contains(result.chain());
    }

    /** A channel that keeps the code instead of sending it, so the test can answer the challenge. */
    private static final class RememberedCodes implements CodeChannel {

        private final FactorType type;
        private String lastCode;

        private RememberedCodes(FactorType type) {
            this.type = type;
        }

        @Override
        public FactorType servesFactor() {
            return type;
        }

        @Override
        public void sendCode(String target, String code) {
            this.lastCode = code;
        }
    }
}
