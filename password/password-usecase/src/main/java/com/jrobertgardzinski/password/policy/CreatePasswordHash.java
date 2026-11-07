package com.jrobertgardzinski.password.policy;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PasswordError;
import com.jrobertgardzinski.password.domain.PasswordStatus;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Judges a plaintext password against the policy in force and, if it passes, hashes it. There is
 * no way to obtain the hash of a password that failed: a refused one comes back
 * {@link PasswordStatus.Rejected} with every broken rule at once, in policy order, so a form can
 * show all of them in one round. A candidate that does not construct (blank) is refused as
 * {@link PasswordError#PASSWORD_BLANK} with the policy skipped. The policy is asked once per
 * attempt, so a refusal names the policy it was judged against.
 */
public class CreatePasswordHash {

    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    private final HashAlgorithmPort hashAlgorithm;
    private final PasswordPolicyInForce passwordPolicyInForce;

    public CreatePasswordHash(HashAlgorithmPort hashAlgorithm, PasswordPolicyInForce passwordPolicyInForce) {
        this.hashAlgorithm = hashAlgorithm;
        this.passwordPolicyInForce = passwordPolicyInForce;
    }

    /**
     * The hash, present only when every rule passed ({@code null} otherwise); the rules broken, in
     * policy order; and the policy they were judged against, because that policy can change between
     * two attempts, so the codes alone would not say what "too short" meant for this one.
     */
    public record Result(HashedPassword hashedPassword, List<PasswordError> errors, PasswordPolicy policy) {
        public Result {
            errors = List.copyOf(errors);
        }

        /** Read off the errors, so the two cannot disagree. */
        public PasswordStatus status() {
            if (errors.isEmpty()) {
                return new PasswordStatus.Passed();
            }
            return new PasswordStatus.Rejected();
        }
    }

    public Result create(Supplier<PlaintextPassword> candidate) {
        PasswordPolicy passwordPolicy = passwordPolicyInForce.current();
        PlaintextPassword plaintextPassword;
        try {
            plaintextPassword = candidate.get();
        } catch (IllegalArgumentException blank) {
            return new Result(null, List.of(PasswordError.PASSWORD_BLANK), passwordPolicy);
        }
        List<PasswordError> errors = new LinkedList<>();
        String password = plaintextPassword.value();
        if (password.length() < passwordPolicy.minLength().value()) {
            errors.add(PasswordError.MIN_LENGTH_NOT_MET);
        }
        if (passwordPolicy.requiresLowercase().value() && !LOWERCASE.matcher(password).find()) {
            errors.add(PasswordError.LOWERCASE_REQUIRED);
        }
        if (passwordPolicy.requiresUppercase().value() && !UPPERCASE.matcher(password).find()) {
            errors.add(PasswordError.UPPERCASE_REQUIRED);
        }
        if (passwordPolicy.requiresDigit().value() && !DIGIT.matcher(password).find()) {
            errors.add(PasswordError.DIGIT_REQUIRED);
        }
        String specialChars = passwordPolicy.specialChars().value();
        if (password.chars().noneMatch(c -> specialChars.indexOf(c) >= 0)) {
            errors.add(PasswordError.SPECIAL_CHAR_REQUIRED);
        }

        if (errors.isEmpty()) {
            return new Result(hashAlgorithm.hash(plaintextPassword), errors, passwordPolicy);
        }
        else {
            return new Result(null, errors, passwordPolicy);
        }
    }
}
