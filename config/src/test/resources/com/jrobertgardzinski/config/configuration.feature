Feature: A deployment's configuration declares a ladder from the rule the code ships

  A rule is a value object that knows its key, the value the code ships and how to hold another
  value through its own constructor. That is everything a ladder needs, so the deployment's
  configuration - its settings table and its properties - declares one from the shipped rule
  alone, and the two ways of reading are named after what changing the value costs: live over
  restart over rebuild for a rule the system asks about per use, restart over rebuild, decided at
  once, for a rule the system holds for its whole life. The text a source holds is parsed by the
  rule's type - an integer, a flag, a constant of an enum - and refused under the ladder's law.

  Background:
    Given a deployment whose settings table and properties are both empty

  Rule: live over the shipped rule answers with the rule holding the value in force, per question

    Scenario: nothing set anywhere - the shipped rule itself
      When the floor is read live over the shipped rule
      Then the floor in force is 5, from the "rebuild (default)" level

    Scenario: a property, then a row: each question climbs the ladder again
      Given the property "sample.floor" is "8"
      When the floor is read live over the shipped rule
      Then the floor in force is 8, from the "restart (properties/env)" level
      When the settings row "sample.floor" becomes "10"
      Then the floor in force is 10, from the "live (database)" level
      When the settings row "sample.floor" is deleted
      Then the floor in force is 8, from the "restart (properties/env)" level

    Scenario: a row the rule refuses is reported and fallen through, the gate being the constructor
      Given the settings row "sample.floor" is "3"
      When the floor is read live over the shipped rule
      Then the floor in force is 5, from the "rebuild (default)" level
      And the report says the "live (database)" level was refused holding 3

  Rule: bound over the shipped rule is decided at once and never asks again

    Scenario: the property is the rule for the process's life
      Given the property "sample.floor" is "8"
      When the floor is bound over the shipped rule
      Then the bound floor is 8
      When the settings row "sample.floor" becomes "10"
      Then the bound floor is still 8

    Scenario: a property the rule refuses refuses the declaration, naming the key and the level
      Given the property "sample.floor" is "3"
      When the floor is bound over the shipped rule
      Then the declaration is refused naming "sample.floor" and the "restart (properties/env)" level

    Scenario: a property that is not the rule's type refuses the declaration the same way
      Given the property "sample.floor" is "five"
      When the floor is bound over the shipped rule
      Then the declaration is refused naming "sample.floor" and the "restart (properties/env)" level

  Rule: the parser comes from the rule's type

    Scenario: a flag reads true and false, any case, and nothing else
      Given the property "sample.strict" is " TRUE "
      When the strictness is bound over the shipped rule
      Then the bound strictness is true
      Given the property "sample.strict" is "yes"
      When the strictness is bound over the shipped rule
      Then the declaration is refused naming "sample.strict" and the "restart (properties/env)" level

    Scenario: an enum reads its constant by name, any case
      Given the property "sample.mode" is "strict"
      When the mode is bound over the shipped rule
      Then the bound mode is STRICT
      Given the property "sample.mode" is "sloppy"
      When the mode is bound over the shipped rule
      Then the declaration is refused naming "sample.mode" and the "restart (properties/env)" level

  Rule: the keys declared live form the catalogue of what the running system may be told

    Scenario: a declared key holds text through the rule's own parser and gate
      When the floor is read live over the shipped rule
      Then the catalogue lists "sample.floor"
      And "sample.floor" told "8" holds 8
      And "sample.floor" told "3" is refused because "floor must be at least 5"
      And "sample.floor" told "eight" is refused because "'eight' is not the type this key takes"

    Scenario: a key nobody declared is not in the catalogue
      When the floor is read live over the shipped rule
      Then the catalogue does not list "sample.strict"

    Scenario: a rule bound over the shipped rule is not live and is not in the catalogue
      When the strictness is bound over the shipped rule
      Then the catalogue does not list "sample.strict"
