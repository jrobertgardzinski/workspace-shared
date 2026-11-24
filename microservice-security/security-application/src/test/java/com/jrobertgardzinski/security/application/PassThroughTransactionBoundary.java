package com.jrobertgardzinski.security.application;

import java.util.function.Supplier;

/** No transaction machinery: the services are tested against in-memory fakes. */
public final class PassThroughTransactionBoundary implements TransactionBoundary {

    @Override
    public <T> T execute(Supplier<T> work) {
        return work.get();
    }
}
