Feature: Configuration ladder

  One key, one precedence law: the level bound latest in the lifecycle wins —
  live (a database row) over restart (a property or environment variable) over
  the rebuild-bound default in the code. Which rungs a key has is the key's own
  business: all three, live over rebuild, restart over rebuild, or rebuild alone.
  Every ladder ends in a rebuild default, so there is always an answer. Every
  candidate passes the same validation gate, but when depends on where the rung
  is bound: the default and the property are fixed before the process serves, so
  an illegal one refuses to build the ladder and the deployment fails at startup;
  only the live rung — written while the system runs — is skipped and the ladder
  falls through. The ladder remembers the climb: which level answered and what
  was refused on the way, so the refusal never has to stay a secret of the log.

  Background:
    Given the validation gate accepts only values of at least 5

  Rule: the highest legal rung answers

    Scenario Outline: a ladder over all three levels
      Given a ladder for "min.length" with rungs live, restart and rebuild default <rebuild>
      And the property "min.length" is <restart>
      And the database row "min.length" <live>
      Then the ladder answers <answer>

      Examples: every level legal — the latest bound wins
        | rebuild | restart   | live      | answer |
        | 8       | set to 12 | holds 10  | 10     |
        | 8       | set to 12 | is absent | 12     |
        | 8       | unset     | is absent | 8      |

      Examples: an illegal row is skipped, not repaired
        | rebuild | restart   | live      | answer |
        | 8       | set to 12 | holds 3   | 12     |
        | 8       | unset     | holds 3   | 8      |

    Scenario: a ladder without a live rung never reads the row
      Given a ladder for "cache.ttl" with rungs restart and rebuild default 10
      And the property "cache.ttl" is set to 30
      And the database row "cache.ttl" holds 60
      Then the ladder answers 30

    Scenario: a ladder without a restart rung never reads the property
      Given a ladder for "free.shipping.from" with rungs live and rebuild default 200
      And the property "free.shipping.from" is set to 100
      And the database row "free.shipping.from" is absent
      Then the ladder answers 200

    Scenario: a ladder of the rebuild default alone is a named constant
      Given a ladder for "return.days" with rungs rebuild default 14
      And the property "return.days" is set to 20
      And the database row "return.days" holds 30
      Then the ladder answers 14

  Rule: the ladder reports which level answered and what it refused on the way

    Scenario: a clean climb names its level and refuses nothing
      Given a ladder for "min.length" with rungs live, restart and rebuild default 8
      And the database row "min.length" holds 10
      Then the answer comes from the "live (database)" level
      And no level was refused

    Scenario: a refused row is reported with its value and the gate's reason
      Given a ladder for "min.length" with rungs live, restart and rebuild default 8
      And the property "min.length" is set to 12
      And the database row "min.length" holds 3
      Then the answer comes from the "restart (properties/env)" level
      And the "live (database)" level was refused holding 3 because "value must be at least 5"

  Rule: a text source is parsed on its rung, and text that is not the type is refused like an illegal value

    # A properties file and a settings table both hold text. The type enters on the rung, through
    # a parser, and the parser's refusal follows the same law as the gate's: at declaration for a
    # level bound before serving, per resolution — skipped, reported — for the live one.

    Scenario: text on every level is parsed and the latest bound wins
      Given a ladder for "min.length" over text rungs live, restart and rebuild default 8
      And the text property "min.length" is set to " 12 "
      And the text row "min.length" holds "10"
      Then the ladder answers 10

    Scenario: a row that is not a number is refused, reported with the text it held, and falls through
      Given a ladder for "min.length" over text rungs live, restart and rebuild default 8
      And the text property "min.length" is set to "12"
      And the text row "min.length" holds "ten"
      Then the ladder answers 12
      And the "live (database)" level was refused holding the text "ten"

    Scenario: a property that is not a number refuses to build the ladder
      Given the text property "min.length" is set to "twelve"
      When a ladder for "min.length" is declared over text rungs live, restart and rebuild default 8
      Then the declaration is rejected naming the "restart (properties/env)" level

  Rule: a refused row is logged once, not once per question

    Scenario: the same illegal row asked a thousand times is one line in the log
      Given a ladder for "min.length" with rungs live, restart and rebuild default 8
      And the database row "min.length" holds 3
      When the ladder is asked 1000 times
      Then the ladder answers 8
      And the refusal was logged once

  Rule: what is bound before the process serves must be legal, or there is no ladder

    Scenario: an illegal default refuses to build the ladder
      When a ladder for "min.length" is declared with rungs live, restart and rebuild default 3
      Then the declaration is rejected naming the "rebuild (default)" level

    Scenario: an illegal property refuses to build a ladder with a live rung
      Given the property "min.length" is set to 4
      When a ladder for "min.length" is declared with rungs live, restart and rebuild default 8
      Then the declaration is rejected naming the "restart (properties/env)" level

    Scenario: an illegal property refuses to build a ladder without a live rung
      Given the property "cache.ttl" is set to 4
      When a ladder for "cache.ttl" is declared with rungs restart and rebuild default 10
      Then the declaration is rejected naming the "restart (properties/env)" level

    Scenario: an illegal property is never rescued by a legal row above it
      Given the property "min.length" is set to 4
      And the database row "min.length" holds 10
      When a ladder for "min.length" is declared with rungs live, restart and rebuild default 8
      Then the declaration is rejected naming the "restart (properties/env)" level

  Rule: a ladder is rungs in descending order ending in the rebuild default

    Scenario: a ladder without a rebuild default is refused
      When a ladder for "min.length" is declared with rungs live and restart only
      Then the declaration is rejected

    Scenario: rungs out of order are refused
      When a ladder for "min.length" is declared with rungs restart, live and rebuild default 8
      Then the declaration is rejected

    Scenario: two rungs on the same level are refused
      When a ladder for "min.length" is declared with rungs live, live and rebuild default 8
      Then the declaration is rejected
