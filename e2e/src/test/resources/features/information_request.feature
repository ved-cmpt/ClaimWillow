Feature: Information requests
  When an adjuster needs more information, the policyholder answers on the claim page and the
  claim goes back to review with a clear record of what changed.

  Scenario: 5. Policyholder answers a request and staff sees the updated case
    Given "Priya" has submitted a claim
    And the claim is in review with "Dana"
    And "Dana" has requested "Please describe the damaged rug and when it was bought."
    And "Priya" is signed in
    When she opens the claim
    Then she sees the adjuster's request "Please describe the damaged rug and when it was bought."
    When she responds "The wool rug was bought in March 2024 for 600 dollars."
    Then the claim shows status "In review"
    When "Dana" is signed in
    And she opens the claim
    Then the notes include "The wool rug was bought in March 2024 for 600 dollars."
    And the history includes "Policyholder responded"
    And the review actions include "Record a decision"
