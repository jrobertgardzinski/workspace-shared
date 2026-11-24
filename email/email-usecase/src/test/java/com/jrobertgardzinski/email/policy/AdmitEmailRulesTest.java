package com.jrobertgardzinski.email.policy;

import com.jrobertgardzinski.email.config.BlockedDomains;
import com.jrobertgardzinski.email.config.CompanyDomains;
import com.jrobertgardzinski.email.config.DisposableDomains;
import com.jrobertgardzinski.email.config.EmailPolicy;
import com.jrobertgardzinski.email.domain.DomainPart;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailError;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Epic("Email")
@Feature("Use case")
@Story("AdmitEmail")
class AdmitEmailRulesTest {

    /** Every rule switched on: one blocked domain, one disposable domain, one company. */
    private static final EmailPolicy EVERY_RULE = new EmailPolicy(
            new BlockedDomains(Set.of(DomainPart.of("blocked.example"))),
            new DisposableDomains(Set.of(DomainPart.of("throwaway.example"))),
            new CompanyDomains(Set.of(DomainPart.of("acme.example"))));

    private final AdmitEmail admitEmail = new AdmitEmail(EVERY_RULE);

    @Property
    @Label("every broken rule is named, in policy order")
    void brokenRulesAreNamedInPolicyOrder(@ForAll("refusals") Tuple.Tuple2<String, List<EmailError>> row) {
        assertThatThrownBy(() -> admitEmail.evaluate(Email.of(row.get1())))
                .isInstanceOfSatisfying(AdmitEmail.Rejected.class, rejected ->
                        assertThat(rejected.errors()).isEqualTo(row.get2()));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, List<EmailError>>> refusals() {
        return Arbitraries.of(
                Tuple.of("user@blocked.example", List.of(EmailError.DOMAIN_BLOCKED, EmailError.NOT_A_COMPANY_DOMAIN)),
                Tuple.of("user@throwaway.example", List.of(EmailError.DISPOSABLE_DOMAIN, EmailError.NOT_A_COMPANY_DOMAIN)),
                Tuple.of("user@other.example", List.of(EmailError.NOT_A_COMPANY_DOMAIN)),
                // constructs (the value checks only the '@' and the dots) but is not an RFC atom
                Tuple.of("user test@acme.example", List.of(EmailError.RFC_FORMAT_INVALID)),
                Tuple.of("user(comment)@acme.example", List.of(EmailError.RFC_FORMAT_INVALID)),
                Tuple.of("user,extra@acme.example", List.of(EmailError.RFC_FORMAT_INVALID)),
                Tuple.of("user@-bad.example", List.of(EmailError.RFC_FORMAT_INVALID, EmailError.NOT_A_COMPANY_DOMAIN)));
    }

    @Example
    @Label("an address that passes every rule is admitted: nothing is thrown")
    void passingAddressIsAllowed() {
        assertThatCode(() -> admitEmail.evaluate(Email.of("user@acme.example"))).doesNotThrowAnyException();
    }

    @Example
    @Label("a rule the deployment did not configure is not applied")
    void absentRulesAreSkipped() {
        assertThatCode(() -> new AdmitEmail(new EmailPolicy()).evaluate(Email.of("user@throwaway.example")))
                .doesNotThrowAnyException();
    }

    @Example
    @Label("a candidate that does not construct is refused by the invariant it broke, and nothing else is asked")
    void malformedCandidateIsRefusedByItsInvariant() {
        assertThatThrownBy(() -> admitEmail.evaluate(() -> Email.of("invalid")))
                .isInstanceOfSatisfying(AdmitEmail.Rejected.class, rejected ->
                        assertThat(rejected.errors()).containsExactly(EmailError.EMAIL_FORMAT_INVALID));
    }

    @Example
    @Label("a candidate that constructs is judged by the rules and handed back")
    void constructingCandidateIsJudgedAndHandedBack() throws Exception {
        assertThat(admitEmail.evaluate(() -> Email.of("user@acme.example"))).isEqualTo(Email.of("user@acme.example"));
    }

    @Example
    @Label("the domain is compared case-insensitively: the value lower-cases it before any rule sees it")
    void domainCaseDoesNotMatter() {
        assertThatThrownBy(() -> admitEmail.evaluate(Email.of("user@Blocked.Example")))
                .isInstanceOfSatisfying(AdmitEmail.Rejected.class, rejected ->
                        assertThat(rejected.errors()).containsExactly(EmailError.DOMAIN_BLOCKED, EmailError.NOT_A_COMPANY_DOMAIN));
    }
}
