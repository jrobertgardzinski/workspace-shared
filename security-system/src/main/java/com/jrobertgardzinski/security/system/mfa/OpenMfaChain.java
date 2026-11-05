package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.Source;

import java.util.List;

public class OpenMfaChain {

    private final MfaChain mfaChain;
    private final PendingAuthenticationStore pendingAuthenticationStore;

    public OpenMfaChain(
                    MfaChain mfaChain,
                    PendingAuthenticationStore pendingAuthenticationStore) {
        this.mfaChain = mfaChain;
        this.pendingAuthenticationStore = pendingAuthenticationStore;
    }

    /** The ticket the proofs are presented against, the factor due first, and its challenge if it has one. */
    public record Result(String ticket, FactorType nextFactor, String challengeData) {}

    public Result open(Email email, List<EnrolledFactor> factors, Source source) {

        // the chain remembers where it began, so a wrong proof is charged to the same pair a wrong
        // password is — see PendingAuthentication. Asked for here, once, so that no way of opening
        // a chain can forget it.
        PendingAuthentication pendingAuthentication = mfaChain.begin(email, factors).startedFrom(source);
        String ticket = pendingAuthenticationStore.open(pendingAuthentication);

        return new Result(
                ticket,
                factors.get(0).type(),
                pendingAuthentication.challengeData());
    }
}
