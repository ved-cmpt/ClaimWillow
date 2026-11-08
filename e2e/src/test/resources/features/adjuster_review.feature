Feature: Adjuster review
  Adjusters pick up submitted claims from the work queue, review them and record decisions.
  Every action is checked by the backend and recorded in the claim history.

  Scenario: 3. Adjuster claims an unassigned case and begins review
    Given "Marcus" has submitted a claim
    And "Dana" is signed in
    When she finds the claim in the work queue and opens it
    And she assigns the claim to herself
    Then the claim is assigned to "Dana Okafor"
    When she starts the review
    Then the claim shows status "In review"

  Scenario: 4. Adjuster requests more information with a mandatory reason
    Given "Marcus" has submitted a claim
    And the claim is in review with "Dana"
    And "Dana" is signed in
    When she opens the claim
    And she requests information without a reason
    Then she sees the message "Explain what you need"
    When she requests information "Please upload the repair estimate for the sofa."
    Then the claim shows status "Needs info"

  Scenario: 6. Adjuster approves a claim with a decision note and closes it
    Given "Elena" has submitted a claim
    And the claim is in review with "Dana"
    And "Dana" is signed in
    When she opens the claim
    And she approves the claim without a note
    Then she sees the message "A decision note is required"
    When she approves the claim with the note "Photos and invoice reviewed; within policy terms."
    Then the claim shows status "Approved"
    When she closes the claim
    Then the claim shows status "Closed"
    And the history includes "Claim approved"

  Scenario: 9. A concurrent staff action results in a safe conflict without data loss
    Given "Marcus" has submitted a claim
    And "Dana" is signed in
    And she opens the claim
    And "Sam" is signed in
    And he opens the claim
    And he assigns the claim to himself
    When "Dana" tries to assign the claim to herself from her stale page
    Then she sees a conflict explaining the claim changed
    When she refreshes the claim
    Then the claim is assigned to "Sam Lindqvist"
    And the claim is still assigned to "Sam" in the system
