# Null safety w warstwie domain (invariants)

❯ Pytam o JSpecify bo zamyśliłem sobie, że każda pojedyncza klasa w vo musi być odporna na przekazanie null, i chciałbym to mieć na papierze tj. raport allure. Chyba jednak najprościej załatwić to przez Objects.requireNonNull.

● Tak, dla tego celu (papier + Allure) Objects.requireNonNull jest praktycznie najlepszy — daje runtime'ową, testowalną ścieżkę, którą można pokryć jednym *NullSafetyRulesTest per VO i wyrenderować jako osobną feature w raporcie.

Trzy rzeczy do zapamiętania przy wprowadzaniu:

1. Kolejność w fabryce — requireNonNull najpierw, dopiero potem reszta walidacji. Wtedy null → NPE, "puste"/"źle sformatowane" → IAE. Czyste rozdzielenie i zgodne z konwencją JDK. U ciebie dziś if (value == null || value.isBlank()) rzuca IAE dla obu przypadków — przy okazji to się zmieni, więc trzeba dotknąć      
   istniejących testów *RulesTest (jeśli sprawdzają typ wyjątku dla nulla).
2. Konstruktory pakietowe/prywatne też — np. Email(LocalPart local, DomainPart domain) przyjmuje VO i też powinien mieć requireNonNull, jeśli reguła jest "każda klasa odporna". Dziś tylko Email.of(String) jest chronione, a paczkowy ctor leci na ślepo.
3. Allure — dla regularności poszedłbym w @Feature("Null safety") + @ParameterizedTest(name = "{0} rejects null") z source listującym fabryki VO, jeden taki test na moduł domain. Wtedy w raporcie widać jednym rzutem oka, że pokrycie jest kompletne, zamiast szukać po *RulesTest-ach.                                 
```
# ADR-00X: No null guards in Value Objects

## Context
System has 3 use cases. Input validation happens
at application layer boundary. Null cannot reach
domain if application layer works correctly.

## Decision
No Objects.requireNonNull() in Value Objects.
Null prevention is responsibility of application
layer, not domain layer.

## Consequences
+ Less boilerplate in domain
+ Cleaner VO constructors
- Domain assumes application layer is correct
- If boundary validation fails, NPE in domain
  (acceptable — it's a programming error, not
  business error)

## Review trigger
Revisit if use cases grow beyond 10
or if external systems start pushing data
directly to domain.
```                                                        
# Ujednolicenie testów w allure

Zanim zaczniesz, porównaj wygenerowaną dokumentację do formatu dokumentacji.

Porównaj sobie email z passwordem, wybierz najlepsze formatowanie i jazda dalej. 

password-security-system
[OK] Password policy