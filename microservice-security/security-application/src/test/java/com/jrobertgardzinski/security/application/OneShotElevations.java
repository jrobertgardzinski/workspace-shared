package com.jrobertgardzinski.security.application;

import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.mfa.SessionElevation;

import java.util.HashSet;
import java.util.Set;

/** One-shot elevations keyed by token and action, as the real store keeps them, minus the clock. */
public final class OneShotElevations implements SessionElevation {

    private final Set<String> granted = new HashSet<>();

    @Override
    public void elevate(String accessToken, StepUpAction action) {
        granted.add(accessToken + "|" + action);
    }

    @Override
    public boolean consume(String accessToken, StepUpAction action) {
        return granted.remove(accessToken + "|" + action);
    }

    /** Whether an elevation is waiting to be spent, without spending it. */
    public boolean holds(String accessToken, StepUpAction action) {
        return granted.contains(accessToken + "|" + action);
    }
}
