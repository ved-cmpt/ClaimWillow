Feature: Access control
  Authorization is enforced by the backend; the UI never reveals claims a user may not see.

  Scenario: 7. A policyholder cannot access another policyholder's claim
    Given "Marcus" has submitted a claim
    And "Priya" is signed in
    When she navigates directly to that claim
    Then she sees that the claim was not found
    And no claim details are shown

  Scenario: 8. An anonymous visitor is redirected away from protected screens
    Given nobody is signed in
    When the visitor opens "/claims"
    Then the sign-in screen is shown
    When the visitor opens "/staff/queue"
    Then the sign-in screen is shown
    When the visitor signs in as "Dana"
    Then the work queue is shown
