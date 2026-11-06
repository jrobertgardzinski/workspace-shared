# unit-of-work

One interface, the promise a protocol needs when two writes must not be separable:

```java
unitOfWork.run(() -> {                 // the implementation opens the transaction
    purgeUserContent.execute(leaver);  // the work
    confirmations.confirm(sagaId);     // and the announcement it must commit with
});
```

It was `Atomically` inside `account-closure`, where the mark and its confirmation needed it.
The deletion cascade owes the same promise, and a vocabulary of closing accounts is not a
dependency of deleting memes — so the promise lives on its own.

Implementations belong to infrastructure: `@Transactional` in Spring, the Micronaut equivalent,
or, in a one-process spec, a lambda that simply runs the step. Pure Java, no dependencies.
