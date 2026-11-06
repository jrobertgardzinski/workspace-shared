package com.jrobertgardzinski.email.policy;

import com.jrobertgardzinski.email.config.BlockedDomains;
import com.jrobertgardzinski.email.config.CompanyDomains;
import com.jrobertgardzinski.email.config.DisposableDomains;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.external.MxRecordPort;
import com.jrobertgardzinski.util.constraint.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Judges whether an address may register: RFC format always, the domain rules it was built with.
 */
public class CanRegister {

    private final Constraints<Email> constraints;

    CanRegister(List<ErrorConstraint<Email>> errorConstraints, List<WarningConstraint<Email>> warningConstraints) {
        this.constraints = new Constraints<>(errorConstraints, warningConstraints);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Outcome<Email> evaluate(Supplier<Email> email) {
        return _EmailCandidate.evaluate(constraints, email);
    }

    public Outcome<Email> evaluate(Email email) {
        constraints.
    }

    public static final class Builder {
        private final List<ErrorConstraint<Email>> errors = new ArrayList<>();
        private final List<WarningConstraint<Email>> warnings = new ArrayList<>();

        private Builder() {
            errors.add(new _RfcFormatConstraint());
        }

        public Builder blockingDomains(BlockedDomains blocked) {
            if (blocked != null) {
                errors.add(new _BlockedDomainConstraint(blocked.values()));
            }
            return this;
        }

        public Builder blockingDisposable(DisposableDomains disposable) {
            if (disposable != null) {
                errors.add(new _DisposableEmailConstraint(disposable.values()));
            }
            return this;
        }

        public Builder requiringCompanyEmployee(CompanyDomains companyDomains) {
            if (companyDomains != null) {
                errors.add(new _IsEmployeeConstraint(companyDomains.values()));
            }
            return this;
        }

        public Builder warningOnMissingMx(MxRecordPort mxRecordPort) {
            if (mxRecordPort != null) {
                warnings.add(new _MxRecordConstraint(mxRecordPort));
            }
            return this;
        }

        public CanRegister build() {
            return new CanRegister(errors, warnings);
        }
    }
}
