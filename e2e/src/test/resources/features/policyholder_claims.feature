Feature: Policyholder claim intake
  Policyholders record damaged or lost belongings, save drafts and submit claims that are
  checked by deterministic rules. All accounts and data are synthetic.

  Scenario: 1. Policyholder signs in, saves a draft and submits a valid claim
    Given "Priya" is signed in
    When she starts a new claim titled "Burst pipe soaked the sofa" for 1800.00 CAD
    And she saves the draft
    Then the draft is saved
    When she reloads the page
    Then the draft still has the title "Burst pipe soaked the sofa"
    When she reviews and submits the claim
    Then the claim shows status "Submitted"
    And the claim has a public reference

  Scenario: 2. Policyholder sees validation warnings and the submitted status
    Given "Priya" is signed in
    When she starts a new claim titled "Flooded storage room" for 65000.00 CAD with description "Water came in."
    And she reviews and submits the claim
    Then the claim shows status "Submitted"
    And the automated checks include "R3 · Estimate vs coverage limit"
    And the automated checks include "R6 · Completeness"
    And the history includes "Automated checks ran"

  Scenario: 10. An invalid attachment is rejected with usable feedback
    Given "Priya" has a draft claim titled "Phone dropped in the lake"
    And "Priya" is signed in
    When she opens the draft
    And she attaches the file "fake-photo.png"
    Then she sees the upload error "extension must match its contents"
    And no attachment is listed
    When she attaches the file "valid-photo.png"
    Then the attachment "valid-photo.png" is listed
