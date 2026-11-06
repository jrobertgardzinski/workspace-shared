package com.jrobertgardzinski.closure;

import com.jrobertgardzinski.identity.UserId;

/** Tells the orchestrator the reversible step is done and how many rows it reserved. */
@FunctionalInterface
public interface ClosureConfirmations {

    void confirm(String sagaId, UserId leaver, int reserved);
}
