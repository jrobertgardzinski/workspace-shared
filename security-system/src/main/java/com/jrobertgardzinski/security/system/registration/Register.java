package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.DomainPart;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailError;
import com.jrobertgardzinski.email.domain.InvalidEmailException;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PasswordError;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.password.policy.PasswordPolicyInForce;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class Register {

    // the same shape email-usecase's _RfcFormatConstraint checks; copied here because that class is package-private
    private static final Pattern RFC_FORMAT = Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+"
                    + "@"
                    + "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"
                    + "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");

    private final UserRepository userRepository;
    private final CanRegisterConfig canRegisterConfig;
    private final CreatePasswordHash createPasswordHash;
    private final PasswordPolicyInForce passwordPolicyInForce;

    public Register(UserRepository userRepository, CanRegisterConfig canRegisterConfig,
                    HashAlgorithmPort hashAlgorithm, PasswordPolicyInForce passwordPolicyInForce) {
        this.userRepository = userRepository;
        this.canRegisterConfig = canRegisterConfig;
        this.createPasswordHash = new CreatePasswordHash(hashAlgorithm);
        this.passwordPolicyInForce = passwordPolicyInForce;
    }

    public RegisterResult execute(Supplier<Email> emailCandidate, Supplier<PlaintextPassword> passwordCandidate) {
        PasswordPolicy passwordPolicy = passwordPolicyInForce.current();

        Email email = null;
        List<EmailError> emailErrors;
        try {
            email = emailCandidate.get();
            emailErrors = policyErrors(email);
        } catch (InvalidEmailException refused) {
            emailErrors = List.of(refused.error());
        }

        // judged even when the address already failed: the form shows both channels in one round.
        // A passing password is hashed here, BEFORE existsBy below: a taken address answers the same
        // 201 as a fresh one, and it must take the same time, or the clock tells the caller what
        // the body hides
        PlaintextPassword password = null;
        List<PasswordError> passwordErrors = List.of();
        try {
            password = passwordCandidate.get();
        } catch (IllegalArgumentException blank) {
            passwordErrors = List.of(PasswordError.PASSWORD_BLANK);
        }
        HashedPassword hashedPassword = null;
        if (password != null) {
            switch (createPasswordHash.create(password, passwordPolicy)) {
                case CreatePasswordHash.Result.Success success -> hashedPassword = success.hashedPassword();
                case CreatePasswordHash.Result.Failure failure -> passwordErrors = failure.errors();
            }
        }

        if (!emailErrors.isEmpty() || !passwordErrors.isEmpty()) {
            return new RegisterResult.Rejected(emailErrors, canRegisterConfig, passwordErrors, passwordPolicy);
        }
        if (userRepository.existsBy(NormalizedEmail.of(email))) {
            return new RegisterResult.EmailAlreadyTaken(email);
        }
        try {
            return new RegisterResult.Registered(userRepository.save(new User(email, hashedPassword)));
        } catch (EmailAlreadyTakenException e) {
            // the storage uniqueness check lost the race after our existsBy check passed
            return new RegisterResult.EmailAlreadyTaken(email);
        }
    }

    private List<EmailError> policyErrors(Email email) {
        List<EmailError> errors = new ArrayList<>();
        DomainPart domain = email.domain();
        if (!RFC_FORMAT.matcher(email.value()).matches()) {
            errors.add(EmailError.RFC_FORMAT_INVALID);
        }
        // a null rule is an absent rule: the deployment did not configure that list
        if (canRegisterConfig.blockedDomains() != null
                && canRegisterConfig.blockedDomains().values().contains(domain)) {
            errors.add(EmailError.DOMAIN_BLOCKED);
        }
        if (canRegisterConfig.disposableDomains() != null
                && canRegisterConfig.disposableDomains().values().contains(domain)) {
            errors.add(EmailError.DISPOSABLE_DOMAIN);
        }
        if (canRegisterConfig.companyDomains() != null
                && !canRegisterConfig.companyDomains().values().contains(domain)) {
            errors.add(EmailError.NOT_A_COMPANY_DOMAIN);
        }
        return errors;
    }
}
