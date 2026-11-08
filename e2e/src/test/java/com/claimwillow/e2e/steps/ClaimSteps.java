package com.claimwillow.e2e.steps;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.claimwillow.e2e.support.AppUnderTest;
import com.claimwillow.e2e.support.World;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

public class ClaimSteps {

    private static final String LONG_DESCRIPTION = "A supply pipe under the kitchen sink burst overnight. Water "
            + "spread into the living room and soaked the sofa and the area rug.";

    private final World world;

    public ClaimSteps(World world) {
        this.world = world;
    }

    // ---- set-up through the API -------------------------------------------------------------

    @Given("{string} is signed in")
    public void isSignedIn(String actor) {
        world.signIn(actor);
    }

    @Given("{string} has submitted a claim")
    public void hasSubmittedClaim(String actor) {
        world.claimTitle = "E2E " + world.runTag + " stereo damaged by water";
        world.claim = world.api(actor).submitClaim(world.claimTitle, "1500.00");
    }

    @Given("{string} has a draft claim titled {string}")
    public void hasDraft(String actor, String title) {
        world.claimTitle = title + " " + world.runTag;
        world.claim = world.api(actor).createDraft(world.claimTitle);
    }

    @Given("the claim is in review with {string}")
    public void inReviewWith(String actor) {
        String id = claimId();
        var api = world.api(actor);
        api.staff(id, "assign", Map.of());
        api.staff(id, "start-review", Map.of());
    }

    @Given("{string} has requested {string}")
    public void hasRequested(String actor, String reason) {
        world.api(actor).staff(claimId(), "request-info", Map.of("reason", reason));
    }

    private String claimId() {
        return world.claim.get("id").asText();
    }

    private String reference() {
        return world.claim.get("reference").asText();
    }

    // ---- policyholder intake ----------------------------------------------------------------

    @When("she/he starts a new claim titled {string} for {double} CAD")
    public void startsClaim(String title, double amount) {
        startsClaimWithDescription(title, amount, LONG_DESCRIPTION);
    }

    @When("she/he starts a new claim titled {string} for {double} CAD with description {string}")
    public void startsClaimWithDescription(String title, double amount, String description) {
        world.claimTitle = title + " " + world.runTag;
        world.clickable("new-claim").click();
        world.byTestId("claim-form");
        Select policy = new Select(world.byTestId("field-policy"));
        WebElement active = policy.getOptions().stream()
                .filter(o -> !o.getAttribute("value").isEmpty() && !o.getText().contains("(expired)"))
                .findFirst().orElseThrow();
        policy.selectByValue(active.getAttribute("value"));
        setDate(world.byTestId("field-incident-date"), LocalDate.now().minusDays(2));
        new Select(world.byTestId("field-incident-type")).selectByValue("DAMAGE");
        world.byTestId("field-title").sendKeys(world.claimTitle);
        world.byTestId("field-description").sendKeys(description);
        world.byTestId("field-estimate").sendKeys(String.format(java.util.Locale.ROOT, "%.2f", amount));
    }

    /** Sets a date input the way a user's date picker would (fires React's input event). */
    private void setDate(WebElement input, LocalDate date) {
        ((JavascriptExecutor) world.driver()).executeScript(
                "const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;"
                        + "setter.call(arguments[0], arguments[1]);"
                        + "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));",
                input, date.toString());
    }

    @When("she/he saves the draft")
    public void savesDraft() {
        world.clickable("save-draft").click();
    }

    @Then("the draft is saved")
    public void draftIsSaved() {
        world.waitForText("toast", "Draft saved");
        world.waiting().until(ExpectedConditions.urlMatches(".*/claims/[0-9a-f-]{36}/edit$"));
    }

    @When("she/he reloads the page")
    public void reloads() {
        world.driver().navigate().refresh();
        world.byTestId("claim-form");
    }

    @Then("the draft still has the title {string}")
    public void draftStillHasTitle(String title) {
        world.waiting().until(ExpectedConditions.attributeContains(World.testId("field-title"), "value", title));
        assertThat(world.byTestId("field-estimate").getAttribute("value")).isEqualTo("1800.00");
    }

    @When("she/he reviews and submits the claim")
    public void reviewsAndSubmits() {
        world.clickable("review-claim").click();
        world.waitForText("claim-review", world.claimTitle);
        world.clickable("submit-claim").click();
        world.byTestId("claim-detail");
    }

    @Then("the claim shows status {string}")
    public void claimShowsStatus(String status) {
        world.waitForText("claim-status", status);
    }

    @Then("the claim has a public reference")
    public void hasReference() {
        assertThat(world.byTestId("claim-reference").getText()).matches("CW-\\d{4}-[2-9A-Z]{6}");
    }

    @Then("the automated checks include {string}")
    public void checksInclude(String text) {
        world.waitForText("findings-card", text);
    }

    @Then("the history includes {string}")
    public void historyIncludes(String text) {
        world.waitForText("timeline-card", text);
    }

    @When("she/he opens the draft")
    public void opensDraft() {
        world.open("/claims/" + claimId() + "/edit");
        world.byTestId("claim-form");
    }

    @When("she/he attaches the file {string}")
    public void attaches(String fixture) {
        WebElement input = world.waiting().until(ExpectedConditions.presenceOfElementLocated(
                World.testId("evidence-input")));
        input.sendKeys(AppUnderTest.fixture(fixture).getAbsolutePath());
    }

    @Then("she/he sees the upload error {string}")
    public void uploadError(String text) {
        world.waitForText("upload-error", text);
    }

    @Then("no attachment is listed")
    public void noAttachment() {
        world.byTestId("no-evidence");
        assertThat(world.driver().findElements(World.testId("attachment"))).isEmpty();
    }

    @Then("the attachment {string} is listed")
    public void attachmentListed(String name) {
        world.waitForText("evidence", name);
        world.waiting().until(ExpectedConditions.invisibilityOfElementLocated(World.testId("upload-error")));
    }

    // ---- staff review -----------------------------------------------------------------------

    @When("she/he finds the claim in the work queue and opens it")
    public void findsInQueue() {
        world.clickable("nav-queue").click();
        WebElement search = world.byTestId("queue-search");
        search.sendKeys(reference(), Keys.ENTER);
        By row = By.cssSelector("[data-testid='queue-row'][data-reference='" + reference() + "']");
        WebElement found = world.waiting().until(ExpectedConditions.visibilityOfElementLocated(row));
        found.findElement(World.testId("queue-open")).click();
        world.waitForText("claim-reference", reference());
    }

    @When("she/he opens the claim")
    public void opensClaim() {
        world.open("/claims/" + claimId());
        world.waitForText("claim-reference", reference());
    }

    @When("she/he assigns the claim to herself/himself")
    public void assignsToSelf() {
        world.clickable("action-assign").click();
        world.waitForText("toast", "Claim assigned to you");
    }

    @Then("the claim is assigned to {string}")
    public void assignedTo(String name) {
        world.waitForText("fact-adjuster", name);
    }

    @When("she/he starts the review")
    public void startsReview() {
        world.clickable("action-start-review").click();
    }

    @When("she/he requests information without a reason")
    public void requestsWithoutReason() {
        world.clickable("action-request-info").click();
    }

    @Then("she/he sees the message {string}")
    public void seesMessage(String text) {
        world.waiting().until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(normalize-space(.), \"" + text + "\") and not(*[contains(normalize-space(.), \""
                        + text + "\")])]")));
        assertThat(world.driver().findElements(World.testId("confirm-dialog"))).isEmpty();
    }

    @When("she/he requests information {string}")
    public void requestsInformation(String reason) {
        world.byTestId("request-reason").sendKeys(reason);
        world.clickable("action-request-info").click();
        world.clickable("confirm-action").click();
    }

    @When("she/he approves the claim without a note")
    public void approvesWithoutNote() {
        world.clickable("decision-approved").click();
        world.clickable("action-decide").click();
    }

    @When("she/he approves the claim with the note {string}")
    public void approvesWithNote(String note) {
        world.clickable("decision-approved").click();
        world.byTestId("decision-reason").sendKeys(note);
        world.clickable("action-decide").click();
        world.waitForText("confirm-dialog", "Approve this claim?");
        world.clickable("confirm-action").click();
    }

    @When("she/he closes the claim")
    public void closesClaim() {
        world.clickable("action-close").click();
        world.waitForText("confirm-dialog", "Close this claim?");
        world.clickable("confirm-action").click();
    }

    @When("{string} tries to assign the claim to herself/himself from her/his stale page")
    public void triesStaleAssign(String actor) {
        world.as(actor);
        world.clickable("action-assign").click();
    }

    @Then("she/he sees a conflict explaining the claim changed")
    public void seesConflict() {
        world.waitForText("action-error", "This claim changed");
        world.waitForText("action-error", "Refresh");
    }

    @When("she/he refreshes the claim")
    public void refreshesClaim() {
        world.clickable("refresh-claim").click();
    }

    @Then("the claim is still assigned to {string} in the system")
    public void stillAssigned(String actor) {
        String assigned = world.api(actor).claim(claimId()).get("assignedAdjuster").get("displayName").asText();
        assertThat(assigned).isEqualTo(actor.equals("Sam") ? "Sam Lindqvist" : "Dana Okafor");
        assertThat(world.driver().findElements(World.testId("action-assign"))).isEmpty();
    }

    // ---- information requests ---------------------------------------------------------------

    @Then("she/he sees the adjuster's request {string}")
    public void seesRequest(String text) {
        world.waitForText("open-request", text);
    }

    @When("she/he responds {string}")
    public void responds(String text) {
        world.byTestId("response-message").sendKeys(text);
        world.clickable("send-response").click();
        world.waitForText("toast", "Response sent");
    }

    @Then("the notes include {string}")
    public void notesInclude(String text) {
        world.waitForText("notes-card", text);
    }

    @Then("the review actions include {string}")
    public void reviewActionsInclude(String text) {
        world.waitForText("staff-panel", text);
    }

    // ---- access control ---------------------------------------------------------------------

    @When("she/he navigates directly to that claim")
    public void navigatesDirectly() {
        world.open("/claims/" + claimId());
    }

    @Then("she/he sees that the claim was not found")
    public void claimNotFound() {
        world.waitForText("claim-not-found", "Claim not found");
    }

    @Then("no claim details are shown")
    public void noDetails() {
        List<WebElement> details = world.driver().findElements(World.testId("claim-detail"));
        assertThat(details).isEmpty();
        assertThat(world.driver().getPageSource()).doesNotContain(world.claimTitle);
    }

    @Given("nobody is signed in")
    public void nobodySignedIn() {
        world.as("Visitor");
    }

    @When("the visitor opens {string}")
    public void visitorOpens(String path) {
        world.open(path);
    }

    @Then("the sign-in screen is shown")
    public void signInShown() {
        world.byTestId("login-form");
        assertThat(world.driver().getCurrentUrl()).contains("/login");
    }

    @When("the visitor signs in as {string}")
    public void visitorSignsIn(String actor) {
        world.byTestId("login-username").sendKeys(world.username(actor));
        world.byTestId("login-password").sendKeys(AppUnderTest.DEMO_PASSWORD);
        world.clickable("login-submit").click();
    }

    @Then("the work queue is shown")
    public void workQueueShown() {
        world.waiting().until(ExpectedConditions.urlContains("/staff/queue"));
        world.byTestId("kpi-new");
    }
}
