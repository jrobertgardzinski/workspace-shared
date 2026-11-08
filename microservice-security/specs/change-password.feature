@ui
Feature: Changing the password

  A signed-in USER changes their password by proving the current one. Afterwards they AUTHENTICATE
  with the new password; the old one no longer works. A wrong current password is rejected. A new
  password that breaks the PASSWORD POLICY is rejected too, saying which rules it broke.

  Background:
    Given a registered USER "user@example.com" with password "OldPassword1!"

  Rule: The correct current password lets the USER CHANGE it

    Example:
      Given the USER has AUTHENTICATED
      When the USER CHANGES the password from "OldPassword1!" to "NewPassword1!"
      Then the USER can AUTHENTICATE with "NewPassword1!"
      And the USER cannot AUTHENTICATE with "OldPassword1!"

  Rule: A wrong current password is rejected

    Example:
      Given the USER has AUTHENTICATED
      When the USER CHANGES the password from "WrongPassword1!" to "NewPassword1!"
      Then the password CHANGE is rejected

  Rule: An account deleted from under a live session is told so, not "wrong password"

    # the deletion happens behind the session's back — a row removed under a live token — which
    # only the JVM glue can stage; the UI's answer to ACCOUNT_GONE (sign out) is unit-tested
    @http-only
    Example:
      Given the USER has AUTHENTICATED
      And the USER's account has been deleted meanwhile
      When the USER CHANGES the password from "OldPassword1!" to "NewPassword1!"
      Then the password CHANGE answers ACCOUNT_GONE

  Rule: A new password that breaks the PASSWORD POLICY is rejected, saying which rules it broke

    Example:
      Given the USER has AUTHENTICATED
      When the USER CHANGES the password from "OldPassword1!" to "weak"
      Then the new password is flagged as invalid
      And the USER can AUTHENTICATE with "OldPassword1!"
