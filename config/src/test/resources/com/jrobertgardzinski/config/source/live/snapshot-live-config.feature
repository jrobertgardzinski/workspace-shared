Feature: The live level as one snapshot of the settings table

  The live level is a copy of the whole settings table, taken when the service starts and again
  after each of the service's own writes: every key is answered from the same read, so a policy
  of five keys costs one round trip and no question ever reaches the table. A writer refreshes
  the snapshot after its own write and sees its decision at once. The table belongs to the
  service and its API is the only way in, so a row written behind the API's back is not noticed
  until the next start or the next write. A table that cannot be read at the start fails the
  start; one that cannot be read after a write fails the write and leaves the snapshot in force
  as it was.

  Scenario: the table is read once, at the start, and answers every key
    Given a settings table where "min.length" is "10" and "special.chars" is "#!"
    When the snapshot is asked for "min.length" and "special.chars" and "requires.digit"
    Then the table was read once
    And the snapshot answers "10" for "min.length"
    And the snapshot answers "#!" for "special.chars"
    And the snapshot answers nothing for "requires.digit"

  Scenario: a change behind the API's back is not seen
    Given a settings table where "min.length" is "10"
    When the table's "min.length" changes to "12"
    Then the snapshot answers "10" for "min.length"

  Scenario: absence is part of the snapshot like a value
    Given an empty settings table
    When the table's "min.length" changes to "10"
    Then the snapshot answers nothing for "min.length"

  Scenario: a writer sees its own decision at once
    Given a settings table where "min.length" is "10"
    When the table's "min.length" changes to "12"
    And the writer refreshes the snapshot
    Then the snapshot answers "12" for "min.length"

  Scenario: an unreadable table at the start fails the start
    Given an unreadable settings table
    Then the snapshot cannot be taken

  Scenario: an unreadable table after a write fails the refresh and keeps the snapshot in force
    Given a settings table where "min.length" is "10"
    When the table's "min.length" changes to "12"
    And the table becomes unreadable
    Then the writer's refresh fails
    And the snapshot answers "10" for "min.length"
