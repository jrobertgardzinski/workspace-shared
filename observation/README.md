# observation

The port a service states its own facts through — **domain probes**, in Pete Hodgson's sense: the
code says what it noticed in the language of the business, and whatever is watching translates that
into its own vocabulary.

```java
// the service's own sealed catalogue — this library never sees one, and must not
public sealed interface Observation {
    record ErasureBacklog(int marked, Duration oldest) implements Observation {}
    record SagaCommandDropped(String topic) implements Observation {}
}

// stated by a use case, which knows nothing about metrics
observations.record(new Observation.ErasureBacklog(3, Duration.ofHours(2)));
```

## Why a port at all

Because the vocabulary of the watching tool is not yours and will not outlive it. `micrometer`,
`prometheus`, `traceparent`, `_total` — none of those words belong to a meme portal, and a service
that spells them throughout its code has to be relearned the day the tool changes. Here they appear
in exactly one adapter per service.

The split that decides what goes where: **the domain is what the tool cannot know on its own.** A
tracing agent knows a method took 40ms. Nothing outside your service can know that content is
sitting marked-but-not-erased, because that is a conclusion drawn from your own rules.

## What the library gives you

- `Observations<F>` — one method, `record(F)`. `F` is your fact type; the library is generic over it
  precisely so it never owns your vocabulary.
- `Observations.silent()` — what a service does when nothing is watching: it goes on working. A
  service with no watcher is not a broken service, so the port has an answer with no adapter
  assembled. That is what lets a composition root leave observability out entirely, and a test drive
  the real work with nobody listening.
- `Observations.fanningOutTo(watchers)` — one fact, several watchers, each called inside its own
  try/catch. This is the shape a migration takes: the new tool runs beside the old one, both fed
  from the same statement, compared before anything is switched off. Because the fact is stated
  ONCE, the watchers cannot disagree about what happened — only about what to call it. And a broken
  exporter is reported through the log rather than through its siblings, so it cannot take them, or
  the work, down with it.

**An implementation must not throw.** Watching is not the work, and a service that fell over because
nobody could write down what it noticed has been broken by its own instrumentation. The fan-out
enforces that for a group; a single adapter is on its honour.

## What it deliberately does not do

No vocabulary, no severity, no naming, no formatting, no transport. Those are the watching tool's
decisions and they are exactly what changes when the tool does.
