package com.jrobertgardzinski.security.system.password;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.CredentialsStatus;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Use case")
@Feature("Verify password")
class VerifyPasswordTest {

    private static final Email EMAIL = Email.of("user@example.com");
    private static final PlaintextPassword PASSWORD = PlaintextPassword.of("plaintext");
    private static final HashedPassword HASH = new HashedPassword("hash");
    private static final HashedPassword ABSENT_ACCOUNT_HASH = new HashedPassword("nobody's");
    private static final User USER = new User(EMAIL, HASH);

    private UserRepository userRepository;
    private HashAlgorithmPort hashAlgorithm;
    private VerifyPassword verifyPassword;

    @BeforeTry
    void init() {
        userRepository = Mockito.mock(UserRepository.class);
        hashAlgorithm = Mockito.mock(HashAlgorithmPort.class);
        // the hash an unknown address is checked against is made once, when the judge is built
        Mockito.when(hashAlgorithm.hash(Mockito.any())).thenReturn(ABSENT_ACCOUNT_HASH);
        verifyPassword = new VerifyPassword(userRepository, hashAlgorithm);
    }

    @Example
    @Label("The account comes back, PASSED, when the password is its own")
    void the_account_when_the_password_matches() {
        Mockito.when(userRepository.findBy(EMAIL)).thenReturn(Optional.of(USER));
        Mockito.when(hashAlgorithm.verify(HASH, PASSWORD)).thenReturn(true);

        VerifyPassword.Result result = verifyPassword.verify(EMAIL, PASSWORD);

        assertAll(
                () -> assertEquals(USER, result.user()),
                () -> assertInstanceOf(CredentialsStatus.Passed.class, result.status())
        );
    }

    @Example
    @Label("WRONG_PASSWORD when the account exists and the password is not its own")
    void wrong_password_when_the_hash_mismatches() {
        Mockito.when(userRepository.findBy(EMAIL)).thenReturn(Optional.of(USER));
        Mockito.when(hashAlgorithm.verify(HASH, PASSWORD)).thenReturn(false);

        VerifyPassword.Result result = verifyPassword.verify(EMAIL, PASSWORD);

        assertAll(
                () -> assertNull(result.user()),
                () -> assertInstanceOf(CredentialsStatus.WrongPassword.class, result.status())
        );
    }

    @Example
    @Label("USER_NOT_FOUND when no account exists — and the password is still hashed, so the clock says nothing")
    void user_not_found_and_the_work_is_done_anyway() {
        Mockito.when(userRepository.findBy(EMAIL)).thenReturn(Optional.empty());

        VerifyPassword.Result result = verifyPassword.verify(EMAIL, PASSWORD);

        assertAll(
                () -> assertNull(result.user()),
                () -> assertInstanceOf(CredentialsStatus.UserNotFound.class, result.status()),
                // skipping the hash for an unknown address answers in a millisecond where a known
                // one costs the full Argon2, and that difference enumerates accounts on endpoints
                // that refuse to enumerate them in words
                () -> Mockito.verify(hashAlgorithm).verify(ABSENT_ACCOUNT_HASH, PASSWORD)
        );
    }

    @Example
    @Label("A match against the stand-in hash still answers USER_NOT_FOUND")
    void nobody_signs_in_as_nobody() {
        Mockito.when(userRepository.findBy(EMAIL)).thenReturn(Optional.empty());
        Mockito.when(hashAlgorithm.verify(ABSENT_ACCOUNT_HASH, PASSWORD)).thenReturn(true);

        VerifyPassword.Result result = verifyPassword.verify(EMAIL, PASSWORD);

        assertInstanceOf(CredentialsStatus.UserNotFound.class, result.status());
    }

    @Example
    @Label("A candidate that does not construct is a wrong password, not an exception")
    void a_blank_candidate_is_a_wrong_password() {
        VerifyPassword.Result result = verifyPassword.verify(EMAIL, () -> PlaintextPassword.of("   "));

        assertAll(
                () -> assertNull(result.user()),
                () -> assertInstanceOf(CredentialsStatus.WrongPassword.class, result.status())
        );
    }
}
