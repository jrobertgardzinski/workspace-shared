package com.jrobertgardzinski.password.policy;

import com.jrobertgardzinski.password.config.MinLength;
import com.jrobertgardzinski.password.config.RequiresDigit;
import com.jrobertgardzinski.password.config.RequiresLowercase;
import com.jrobertgardzinski.password.config.RequiresUppercase;
import com.jrobertgardzinski.password.config.SpecialChars;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PasswordError;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Epic("Password")
@Feature("Use case")
@Story("Create Password Hash")
class CreatePasswordHashRulesTest {

    /** min 5, special "!@#$%^&*", lowercase + uppercase + digit required. */
    private static final PasswordPolicy DEFAULTS = PasswordPolicy.withDefaults();

    @Property
    @Label("every broken rule is named, in policy order, with the policy it was judged against; nothing is hashed")
    void brokenRulesAreNamedInPolicyOrder(@ForAll("refusals") Tuple.Tuple2<String, List<PasswordError>> row) {
        CreatePasswordHash useCase = new CreatePasswordHash(explodingAlgorithm(), () -> DEFAULTS);

        assertThatThrownBy(() -> useCase.create(() -> PlaintextPassword.of(row.get1())))
                .isInstanceOfSatisfying(CreatePasswordHash.Rejected.class, rejected -> {
                    assertThat(rejected.errors()).isEqualTo(row.get2());
                    assertThat(rejected.policy()).isEqualTo(DEFAULTS);
                });
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, List<PasswordError>>> refusals() {
        return Arbitraries.of(
                Tuple.of("weak", List.of(PasswordError.MIN_LENGTH_NOT_MET, PasswordError.UPPERCASE_REQUIRED,
                        PasswordError.DIGIT_REQUIRED, PasswordError.SPECIAL_CHAR_REQUIRED)),
                Tuple.of("Ab1!", List.of(PasswordError.MIN_LENGTH_NOT_MET)),
                Tuple.of("STRONGPASS1!", List.of(PasswordError.LOWERCASE_REQUIRED)),
                Tuple.of("strongpass1!", List.of(PasswordError.UPPERCASE_REQUIRED)),
                Tuple.of("StrongPass!", List.of(PasswordError.DIGIT_REQUIRED)),
                Tuple.of("StrongPass1", List.of(PasswordError.SPECIAL_CHAR_REQUIRED)),
                Tuple.of("StrongPass1?", List.of(PasswordError.SPECIAL_CHAR_REQUIRED)));
    }

    @Example
    @Label("a password that meets every rule comes back hashed by the algorithm")
    void passingPasswordIsHashed() throws Exception {
        CreatePasswordHash useCase = new CreatePasswordHash(stubAlgorithm(), () -> DEFAULTS);

        assertThat(useCase.create(() -> PlaintextPassword.of("Str0ng!pass"))).isEqualTo(new HashedPassword("hash:Str0ng!pass"));
    }

    @Example
    @Label("a rule switched off in the policy is not applied")
    void switchedOffRulesAreNotApplied() throws Exception {
        PasswordPolicy lenient = new PasswordPolicy(MinLength.DEFAULT, SpecialChars.DEFAULT,
                new RequiresUppercase(false), new RequiresLowercase(false), new RequiresDigit(false));
        CreatePasswordHash useCase = new CreatePasswordHash(stubAlgorithm(), () -> lenient);

        assertThat(useCase.create(() -> PlaintextPassword.of("!!!!!"))).isEqualTo(new HashedPassword("hash:!!!!!"));
    }

    @Example
    @Label("the minimum length and the special characters are the policy's, not the defaults, and the refusal says which policy")
    void parametersComeFromThePolicy() {
        PasswordPolicy strict = PasswordPolicy.defaultsExcept(new MinLength(12), new SpecialChars("#?!"));
        CreatePasswordHash useCase = new CreatePasswordHash(explodingAlgorithm(), () -> strict);

        assertThatThrownBy(() -> useCase.create(() -> PlaintextPassword.of("Str0ngPass*")))
                .isInstanceOfSatisfying(CreatePasswordHash.Rejected.class, rejected -> {
                    assertThat(rejected.errors()).containsExactly(PasswordError.MIN_LENGTH_NOT_MET, PasswordError.SPECIAL_CHAR_REQUIRED);
                    assertThat(rejected.policy()).isEqualTo(strict);
                });
    }

    @Example
    @Label("a candidate that does not construct is PASSWORD_BLANK alone, the policy skipped, nothing hashed")
    void blankCandidateIsACodeOfItsOwn() {
        CreatePasswordHash useCase = new CreatePasswordHash(explodingAlgorithm(), () -> DEFAULTS);

        assertThatThrownBy(() -> useCase.create(() -> PlaintextPassword.of("   ")))
                .isInstanceOfSatisfying(CreatePasswordHash.Rejected.class, rejected -> {
                    assertThat(rejected.errors()).containsExactly(PasswordError.PASSWORD_BLANK);
                    assertThat(rejected.policy()).isEqualTo(DEFAULTS);
                });
    }

    @Example
    @Label("the policy is asked per attempt: the same judge, two attempts, two policies named")
    void policyIsAskedPerAttempt() {
        PasswordPolicy strict = PasswordPolicy.defaultsExcept(new MinLength(12), SpecialChars.DEFAULT);
        AtomicReference<PasswordPolicy> inForce = new AtomicReference<>(DEFAULTS);
        CreatePasswordHash useCase = new CreatePasswordHash(explodingAlgorithm(), inForce::get);

        assertThatThrownBy(() -> useCase.create(() -> PlaintextPassword.of("weak")))
                .isInstanceOfSatisfying(CreatePasswordHash.Rejected.class, rejected ->
                        assertThat(rejected.policy()).isEqualTo(DEFAULTS));
        inForce.set(strict);
        assertThatThrownBy(() -> useCase.create(() -> PlaintextPassword.of("Str0ng!pass")))
                .isInstanceOfSatisfying(CreatePasswordHash.Rejected.class, rejected -> {
                    assertThat(rejected.errors()).containsExactly(PasswordError.MIN_LENGTH_NOT_MET);
                    assertThat(rejected.policy()).isEqualTo(strict);
                });
    }

    // --- stubs ---

    /** Proves nothing was hashed: being called at all is the failure. */
    private static HashAlgorithmPort explodingAlgorithm() {
        return new HashAlgorithmPort() {
            @Override public HashedPassword hash(PlaintextPassword p) {
                throw new AssertionError("must not hash a refused password");
            }
            @Override public boolean verify(HashedPassword h, PlaintextPassword p) { return false; }
        };
    }

    private static HashAlgorithmPort stubAlgorithm() {
        return new HashAlgorithmPort() {
            @Override public HashedPassword hash(PlaintextPassword p) { return new HashedPassword("hash:" + p.value()); }
            @Override public boolean verify(HashedPassword h, PlaintextPassword p) { return false; }
        };
    }
}
