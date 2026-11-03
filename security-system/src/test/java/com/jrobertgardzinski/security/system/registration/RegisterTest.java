package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.BlockedDomains;
import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.config.CompanyDomains;
import com.jrobertgardzinski.email.config.DisposableDomains;
import com.jrobertgardzinski.email.domain.DomainPart;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailError;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PasswordError;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Epic("Use case")
@Feature("Register")
class RegisterTest {

    private static final String EMAIL = "user@example.com";
    private static final String BLOCKED_EMAIL = "user@blocked.example";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String WEAK_PASSWORD = "weak";
    private static final HashedPassword HASH = new HashedPassword("hash:" + PASSWORD);
    /** The email policy in force for every attempt here: one blocked domain, no other rule. */
    private static final CanRegisterConfig EMAIL_POLICY = new CanRegisterConfig(
            new BlockedDomains(Set.of(DomainPart.of("blocked.example"))), null, null);
    /** Every rule switched on, for the examples that walk the rules one by one. */
    private static final CanRegisterConfig EVERY_RULE = new CanRegisterConfig(
            new BlockedDomains(Set.of(DomainPart.of("blocked.example"))),
            new DisposableDomains(Set.of(DomainPart.of("throwaway.example"))),
            new CompanyDomains(Set.of(DomainPart.of("acme.example"))));

    /** Counts how often it was asked, so a test can prove a refused attempt cost no hash. */
    private static final class CountingAlgorithm implements HashAlgorithmPort {
        final AtomicInteger hashes = new AtomicInteger();

        @Override
        public HashedPassword hash(PlaintextPassword plaintextPassword) {
            hashes.incrementAndGet();
            return new HashedPassword("hash:" + plaintextPassword.value());
        }

        @Override
        public boolean verify(HashedPassword hashedPassword, PlaintextPassword plaintextPassword) {
            return hashedPassword.value().equals("hash:" + plaintextPassword.value());
        }
    }

    private UserRepository userRepository;
    private CountingAlgorithm algorithm;
    private Register register;

    @BeforeTry
    void init() {
        userRepository = Mockito.mock(UserRepository.class);
        algorithm = new CountingAlgorithm();
        register = new Register(userRepository, EMAIL_POLICY, algorithm, PasswordPolicy::withDefaults);
    }

    @Property
    @Label("Rejected when email, password, or both fail validation — with both policies in force attached")
    void rejected_when_any_validation_fails(
            @ForAll boolean emailFails,
            @ForAll boolean passwordFails) {

        Assume.that(emailFails || passwordFails);

        String email = emailFails ? BLOCKED_EMAIL : EMAIL;
        String password = passwordFails ? WEAK_PASSWORD : PASSWORD;

        RegisterResult result = register.execute(() -> Email.of(email), () -> PlaintextPassword.of(password));

        RegisterResult.Rejected rejected = assertInstanceOf(RegisterResult.Rejected.class, result);
        assertAll(
                () -> assertEquals(emailFails ? List.of(EmailError.DOMAIN_BLOCKED) : List.of(), rejected.emailErrors()),
                () -> assertEquals(passwordFails, !rejected.passwordErrors().isEmpty()),
                () -> assertEquals(EMAIL_POLICY, rejected.emailPolicy()),
                () -> assertEquals(PasswordPolicy.withDefaults(), rejected.passwordPolicy()),
                () -> Mockito.verify(userRepository, Mockito.never()).save(Mockito.any())
        );
    }

    @Example
    @Label("both channels are refused at once, each rule by its code, in policy order")
    void both_channels_refused_at_once() {
        RegisterResult result = register.execute(() -> Email.of(BLOCKED_EMAIL), () -> PlaintextPassword.of(WEAK_PASSWORD));

        RegisterResult.Rejected rejected = assertInstanceOf(RegisterResult.Rejected.class, result);
        assertAll(
                () -> assertEquals(List.of(EmailError.DOMAIN_BLOCKED), rejected.emailErrors()),
                () -> assertEquals(List.of(PasswordError.MIN_LENGTH_NOT_MET, PasswordError.UPPERCASE_REQUIRED,
                        PasswordError.DIGIT_REQUIRED, PasswordError.SPECIAL_CHAR_REQUIRED), rejected.passwordErrors()),
                () -> assertEquals(0, algorithm.hashes.get(), "a refused password is never hashed")
        );
    }

    @Example
    @Label("an address that does not construct is a code, not an exception — and the password is still judged")
    void malformed_address_is_a_code_and_the_password_is_still_judged() {
        RegisterResult result = register.execute(() -> Email.of("invalid"), () -> PlaintextPassword.of(WEAK_PASSWORD));

        RegisterResult.Rejected rejected = assertInstanceOf(RegisterResult.Rejected.class, result);
        assertAll(
                () -> assertEquals(List.of(EmailError.EMAIL_FORMAT_INVALID), rejected.emailErrors()),
                () -> assertFalse(rejected.passwordErrors().isEmpty(), "the password channel must be judged too"),
                () -> Mockito.verify(userRepository, Mockito.never()).save(Mockito.any())
        );
    }

    @Example
    @Label("a blank password is a code of its own, with the policy skipped and nothing hashed")
    void blank_password_is_a_code() {
        RegisterResult result = register.execute(() -> Email.of("a..b@gmail.com"), () -> PlaintextPassword.of("   "));

        RegisterResult.Rejected rejected = assertInstanceOf(RegisterResult.Rejected.class, result);
        assertAll(
                () -> assertEquals(List.of(EmailError.LOCAL_PART_CONSECUTIVE_DOTS), rejected.emailErrors()),
                () -> assertEquals(List.of(PasswordError.PASSWORD_BLANK), rejected.passwordErrors()),
                () -> assertEquals(0, algorithm.hashes.get())
        );
    }

    @Property
    @Label("each email rule answers with its own code; a domain that passes every rule answers with none")
    void each_email_rule_by_its_code(@ForAll("addressesUnderEveryRule") Tuple.Tuple2<String, List<EmailError>> row) {
        Register strict = new Register(userRepository, EVERY_RULE, algorithm, PasswordPolicy::withDefaults);

        RegisterResult result = strict.execute(() -> Email.of(row.get1()), () -> PlaintextPassword.of(PASSWORD));

        if (row.get2().isEmpty()) {
            assertInstanceOf(RegisterResult.Registered.class, result);
        } else {
            assertEquals(row.get2(), assertInstanceOf(RegisterResult.Rejected.class, result).emailErrors());
        }
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, List<EmailError>>> addressesUnderEveryRule() {
        return Arbitraries.of(
                Tuple.of("user@acme.example", List.of()),
                Tuple.of("user@blocked.example", List.of(EmailError.DOMAIN_BLOCKED, EmailError.NOT_A_COMPANY_DOMAIN)),
                Tuple.of("user@throwaway.example", List.of(EmailError.DISPOSABLE_DOMAIN, EmailError.NOT_A_COMPANY_DOMAIN)),
                Tuple.of("user@other.example", List.of(EmailError.NOT_A_COMPANY_DOMAIN)),
                // a space is not in the RFC atom, though the address still constructs
                Tuple.of("jo hn@acme.example", List.of(EmailError.RFC_FORMAT_INVALID)));
    }

    @Example
    @Label("a deployment without email rules admits any well-formed address")
    void absent_rules_are_skipped() {
        Register lenient = new Register(userRepository, new CanRegisterConfig(), algorithm, PasswordPolicy::withDefaults);
        Mockito.when(userRepository.save(Mockito.any())).thenAnswer(call -> call.getArgument(0));

        RegisterResult result = lenient.execute(() -> Email.of("user@throwaway.example"), () -> PlaintextPassword.of(PASSWORD));

        assertInstanceOf(RegisterResult.Registered.class, result);
    }

    @Example
    @Label("Registered when both email and password pass validation")
    void registered_when_both_pass() {
        User user = new User(Email.of(EMAIL), HASH);
        Mockito.when(userRepository.save(Mockito.any())).thenReturn(user);

        RegisterResult result = register.execute(() -> Email.of(EMAIL), () -> PlaintextPassword.of(PASSWORD));

        RegisterResult.Registered registered = assertInstanceOf(RegisterResult.Registered.class, result);
        assertEquals(user, registered.user());
    }

    @Example
    @Label("EmailAlreadyTaken when a user with that email already exists — after the hash was paid, like a fresh one")
    void email_already_taken_when_user_exists() {
        Mockito.when(userRepository.existsBy(Mockito.any())).thenReturn(true);

        RegisterResult result = register.execute(() -> Email.of(EMAIL), () -> PlaintextPassword.of(PASSWORD));

        RegisterResult.EmailAlreadyTaken alreadyTaken = assertInstanceOf(RegisterResult.EmailAlreadyTaken.class, result);
        assertAll(
                () -> assertEquals(Email.of(EMAIL), alreadyTaken.email()),
                () -> assertEquals(1, algorithm.hashes.get(), "a taken address must cost the same hash as a fresh one"),
                () -> Mockito.verify(userRepository, Mockito.never()).save(Mockito.any())
        );
    }

    @Example
    @Label("EmailAlreadyTaken when the storage loses the race after existsBy said no")
    void email_already_taken_when_save_loses_the_race() {
        Mockito.when(userRepository.existsBy(Mockito.any())).thenReturn(false);
        Mockito.when(userRepository.save(Mockito.any())).thenThrow(new EmailAlreadyTakenException());

        RegisterResult result = register.execute(() -> Email.of(EMAIL), () -> PlaintextPassword.of(PASSWORD));

        assertEquals(Email.of(EMAIL), assertInstanceOf(RegisterResult.EmailAlreadyTaken.class, result).email());
    }
}
