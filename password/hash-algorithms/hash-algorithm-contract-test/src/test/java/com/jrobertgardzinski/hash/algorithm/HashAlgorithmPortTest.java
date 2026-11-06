package com.jrobertgardzinski.hash.algorithm;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import static org.assertj.core.api.Assertions.assertThat;


@Epic("Hash Algorithm: Argon2")
@Feature("Contract")
@Story("password verification")
public abstract class HashAlgorithmPortTest {

    private static final String INPUT = "StrongPassword1!";

    protected abstract HashAlgorithmPort hashAlgorithm();

    @Example
    @Label("correct password verifies")
    void correctPasswordVerifies() {
        HashedPassword hash = hashAlgorithm().hash(PlaintextPassword.of(INPUT));
        Allure.parameter("algorithm", hashAlgorithm().getClass().getSimpleName());
        Allure.parameter("entered password", INPUT);
        Allure.parameter("correct password", INPUT);
        assertThat(hashAlgorithm().verify(hash, PlaintextPassword.of(INPUT))).isTrue();
    }

    @Example
    @Label("wrong password does not verify")
    void wrongPasswordDoesNotVerify() {
        HashedPassword hash = hashAlgorithm().hash(PlaintextPassword.of(INPUT));
        Allure.parameter("algorithm", hashAlgorithm().getClass().getSimpleName());
        Allure.parameter("entered password", INPUT + "23");
        Allure.parameter("correct password", INPUT);
        assertThat(hashAlgorithm().verify(hash, PlaintextPassword.of(INPUT + "23"))).isFalse();
    }
}
