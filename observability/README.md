# Observability — konwencje stacku

## Format logów (jeden wzorzec dla wszystkich serwisów)

```
YYYY-MM-DDTHH:mm:ss.SSS±TZ LEVEL [cid=<correlation-id>] [trace=<trace-id>] <logger> - <message>
```

- **timestamp ISO-8601 z milisekundami i strefą** — Loki i człowiek sortują to samo;
- **LEVEL** wyrównany do 5 znaków;
- **`[cid=…]`** — correlation-id żądania (filtr per serwis, wędruje HTTP-em i Kafką);
  serwisy bez cid drukują `[cid=-]` — kolumny się nie przesuwają;
- **`[trace=…]`** — trace-id z agenta OpenTelemetry (klucz MDC `trace_id`); dzięki temu
  linia loguje się wprost do trace'u w Tempo (i odwrotnie: tracesToLogsV2 w Grafanie);
- logger skrócony, po myślniku goła wiadomość.

Grep jednego żądania przez wszystkie serwisy (Grafana → Loki):
`{service=~".+"} |= "cid=<CID>"` · skok do trace'u: skopiuj `trace=` do Tempo.

## Przepisy per stack

| Stack | Gdzie | Jak |
|---|---|---|
| logback (Micronaut/Spring/collections/paddock) | `logback.xml` / `logging.pattern.console` | `%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [cid=%X{cid:-}] [trace=%X{trace_id:-}] %logger{24} - %msg%n` |
| Quarkus (email) | `quarkus.log.console.format` | `%d{yyyy-MM-dd'T'HH:mm:ss.SSSZZZ} %-5p [cid=%X{cid}] [trace=%X{trace_id}] %c{2.} - %s%e%n` |
| czysty JDK (formula) | JUL `SimpleFormatter.format` w `Main` | `%1$tFT%1$tT.%1$tL%1$tz %4$-5s [cid=-] [trace=-] %3$s - %5$s%6$s%n` |
| Python stdlib (race-sim, idp, sms, push, image) | helper `log()` w serwerze | f-string wg wzorca, `[cid=-] [trace=-]` |

Uwaga: `trace_id` w MDC pojawia się tylko, gdy serwis biegnie z agentem OTel
(compose tak biegnie); bez agenta pole drukuje się puste — to celowe.

## Pozostałe konwencje

- Metryki: `/prometheus` (Micronaut), `/q/metrics` (Quarkus), `/actuator/prometheus`
  (Spring), `/metrics` (ręczne: paddock, formula, race-sim). Scrape: `prometheus.yml`.
- Logi: Promtail → Loki, etykieta `service` = nazwa z compose.
- Trace'y: agent OTel (`x-otel-agent` w compose) → Tempo; Grafana skacze trace↔logi.
- Alerty: `alert-rules.yml` (Prometheus /alerts; Alertmanager = produkcja).
