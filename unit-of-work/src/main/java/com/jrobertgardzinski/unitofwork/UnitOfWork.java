package com.jrobertgardzinski.unitofwork;

/** One unit of work: everything the step does commits together, or not at all. */
@FunctionalInterface
public interface UnitOfWork {

    void run(Runnable step);
}
