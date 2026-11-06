package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.port.CodeChannel;
import com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.Source;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A factor chain is opened in one place, and that place demands the source.
 *
 * <p>The password sign-in remembered where its chain began and the federated one did not, so a
 * wrong second-factor code after a provider login was charged to nobody: the per-ticket limit was
 * the only brake, and a new ticket cost one more provider round trip. With the source a parameter
 * here, no way of opening a chain can forget it.
 */
@Epic("Multi-factor sign-in")
@Feature("Opening the chain")
class OpenMfaChainTest {

    private static final Email USER = Email.of("chain@example.com");
    private static final Source SOURCE = Source.of(new IpAddress("203.0.113.9"));

    @Test
    @DisplayName("the opened chain remembers where it began, so a wrong proof has a pair to be charged to")
    void the_opened_chain_remembers_its_source() {
        CodeChannel mailbox = new CodeChannel() {
            @Override
            public FactorType servesFactor() {
                return FactorType.EMAIL_CODE;
            }

            @Override
            public void sendCode(String target, String code) {
            }
        };
        MfaChain mfaChain = new MfaChain(
                new FactorRegistry(List.of(new CodeFactor(
                        mailbox, raw -> "hash:" + raw, ChallengeCodeConfig.withDefaults(), Clock.systemUTC()))),
                ChallengeCodeConfig.withDefaults(),
                Mockito.mock(RecoveryCodeRepository.class),
                raw -> "hash:" + raw, Clock.systemUTC(), 10);
        FakePendingAuthenticationStore store = new FakePendingAuthenticationStore();
        EnrolledFactor emailFactor = new EnrolledFactor(
                USER, FactorType.EMAIL_CODE, FactorLabels.of(FactorType.EMAIL_CODE), 0, USER.value());

        OpenMfaChain.Result result = new OpenMfaChain(mfaChain, store).open(USER, List.of(emailFactor), SOURCE);

        PendingAuthentication pending = store.find(result.ticket()).orElseThrow();
        assertThat(result.nextFactor()).isEqualTo(FactorType.EMAIL_CODE);
        assertThat(pending.source()).contains(SOURCE);
        assertThat(pending.lockoutSubject())
                .as("without it ContinueAuthentication has nobody to write a wrong proof against")
                .isPresent();
    }
}
