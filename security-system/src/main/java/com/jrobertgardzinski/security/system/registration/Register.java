package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.DomainPart;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.password.policy.PasswordPolicyInForce;
import com.jrobertgardzinski.security.domain.vo.RegisterRequest;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

public class Register {
    private final UserRepository userRepository;
    private final CanRegisterConfig canRegisterConfig;
    private final HashAlgorithmPort hashAlgorithm;
    private final PasswordPolicyInForce passwordPolicyInForce;

    public Register(UserRepository userRepository, CanRegisterConfig canRegisterConfig,
                    HashAlgorithmPort hashAlgorithm, PasswordPolicyInForce passwordPolicyInForce) {
        this.userRepository = userRepository;
        this.canRegisterConfig = canRegisterConfig;
        this.hashAlgorithm = hashAlgorithm;
        this.passwordPolicyInForce = passwordPolicyInForce;
    }

    public RegisterResult execute(Supplier<Email> email, Supplier<PlaintextPassword> password) {


        return new RegistrationAttempt(
                _EmailVerdict.judge(canRegisterConfig, email),
                _PasswordVerdict.judge(hashAlgorithm, passwordPolicyInForce.current(), password),
                userRepository
        ).resolve();
    }

    public RegisterResult execute2(RegisterRequest registerRequest) {
        Email email = registerRequest.getEmail();
        PlaintextPassword plaintextPassword = registerRequest.getPlaintextPassword();

        CanRegister canRegister = CanRegister.builder()
                .blockingDomains(canRegisterConfig.blockedDomains())
                .blockingDisposable(canRegisterConfig.disposableDomains())
                .requiringCompanyEmployee(canRegisterConfig.companyDomains())
                .build();

        if (!collectErrorsForEmail(email.domain()).isEmpty()) {
            return new RegisterResult.EmailAlreadyTaken(email);
        }

        PasswordPolicy ab = passwordPolicyInForce.current();
        if (!blah(plaintextPassword, ab).isEmpty()) {
            return new RegisterResult.Rejected(em)
        }
        CreatePasswordHash createPasswordHash = new CreatePasswordHash(hashAlgorithm, passwordPolicyInForce.current());

        //canRegister.getCon2().stream().

        // dobra chuj, dalej nie dam rady. Chciałem po prostu w tym miejscu w prosty sposób zwalidować email podany do rejestracji i wyjebać error, potem jeszcze password do walidacji i też wyjebać error albo dać sukces. Dasz rade to ogarnąć?
    }

    private List<String> blah(PlaintextPassword plaintextPassword, PasswordPolicy ab) {
        List<String> errors = new LinkedList<>();
        if (ab.minLength().value() > plaintextPassword.value().length()) {
            errors.add("minLength");
        }
        return errors;
    }

    private List<String> collectErrorsForEmail(DomainPart domainPart) {
        List<String> emailErrors = new LinkedList<>();
        if (canRegisterConfig.blockedDomains().values().contains(domainPart)) {
            emailErrors.add("blocked");
        }
        if (canRegisterConfig.disposableDomains().values().contains(domainPart)) {
            emailErrors.add("disposable");
        }
        if (!canRegisterConfig.companyDomains().values().contains(domainPart)) {
            emailErrors.add("company");
        }
        return emailErrors;
    }
}
