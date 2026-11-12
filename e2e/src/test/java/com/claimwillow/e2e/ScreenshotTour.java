package com.claimwillow.e2e;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.claimwillow.e2e.support.World;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * Captures the README screenshots from a freshly seeded disposable instance. Not part of the
 * acceptance suite; run on demand:
 * {@code ./mvnw test -Dtest=ScreenshotTour -Dsurefire.failIfNoSpecifiedTests=false}
 */
class ScreenshotTour {

    private final World world = new World();
    private final Path out = Path.of("..", "docs", "screenshots");

    private void shot(String name) throws Exception {
        Files.createDirectories(out);
        byte[] png = ((TakesScreenshot) world.driver()).getScreenshotAs(OutputType.BYTES);
        Files.write(out.resolve(name + ".png"), png);
    }

    private void size(int width, int height) {
        world.driver().manage().window().setSize(new Dimension(width, height));
    }

    @Test
    void captureTour() throws Exception {
        try {
            // Data for the walkthrough, created through the public API.
            JsonNode review = world.api("Priya").submitClaim("Ceiling leak damaged the bookcase and rug",
                    "68250.00", "Water from the unit upstairs came through the ceiling overnight. The oak bookcase, about "
                            + "200 books and a wool rug were soaked; the building manager has the plumber's report.");
            String reviewId = review.get("id").asText();
            var dana = world.api("Dana");
            dana.staff(reviewId, "assign", Map.of());
            dana.staff(reviewId, "start-review", Map.of());
            dana.send("POST", "/api/v1/staff/claims/" + reviewId + "/notes",
                    Map.of("body", "Estimate exceeds the coverage limit; confirm item list with the policyholder."));

            WebDriver driver = world.as("Visitor");
            size(1440, 960);
            world.open("/");
            world.byTestId("demo-adjuster");
            world.byTestId("hero-timeline");
            world.waitForText("hero-timeline", "Closed");
            shot("00-landing");

            size(390, 844);
            world.open("/");
            world.byTestId("demo-policyholder");
            shot("10-mobile-landing");

            // One-click demo: the landing page button signs in through the normal login API.
            size(1440, 960);
            world.open("/");
            world.clickable("demo-adjuster").click();
            world.waiting().until(ExpectedConditions.urlContains("/staff/queue"));
            world.byTestId("queue-table");
            world.clickable("sign-out").click();
            world.byTestId("landing");

            world.open("/login");
            world.byTestId("login-form");
            shot("01-sign-in");

            world.signIn("Priya");
            size(1440, 960);
            world.byTestId("my-claims-table");
            shot("02-my-claims");

            world.clickable("new-claim").click();
            world.byTestId("claim-form");
            shot("03-new-claim-form");

            world.open("/claims/" + reviewId);
            world.byTestId("finding");
            world.waitForText("timeline-card", "Review started");
            shot("04-claim-detail-policyholder");

            world.signIn("Dana");
            size(1440, 960);
            world.open("/staff/queue");
            world.byTestId("queue-table");
            shot("05-staff-work-queue");

            world.open("/claims/" + reviewId);
            world.byTestId("staff-panel");
            world.byTestId("finding");
            world.byTestId("note");
            shot("06-staff-review");

            world.open("/reports");
            world.byTestId("report-table");
            shot("07-reports");

            size(390, 844);
            world.open("/staff/queue");
            world.byTestId("queue-table");
            shot("08-mobile-work-queue");
            world.open("/claims/" + reviewId);
            world.byTestId("staff-panel");
            world.waiting().until(ExpectedConditions.visibilityOfElementLocated(World.testId("finding")));
            shot("09-mobile-staff-review");
            world.clickable("open-menu").click();
            world.waiting().until(ExpectedConditions.elementToBeClickable(World.testId("nav-reports")));
            shot("11-mobile-navigation");
            driver.manage().window().setSize(new Dimension(1440, 960));
        } finally {
            world.quitAll();
        }
    }
}
