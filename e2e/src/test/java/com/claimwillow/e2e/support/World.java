package com.claimwillow.e2e.support;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Per-scenario state shared between step classes (injected by PicoContainer). Each named actor
 * gets an isolated browser (separate cookies/session), so two adjusters can work at once.
 */
public class World {

    public static final Map<String, String> ACCOUNTS = Map.of(
            "Priya", "priya@example.com",
            "Marcus", "marcus@example.com",
            "Elena", "elena@example.com",
            "Dana", "dana.adjuster@example.com",
            "Sam", "sam.adjuster@example.com",
            "Robin", "admin@example.com");

    private final AppUnderTest app = AppUnderTest.get();
    private final Map<String, WebDriver> browsers = new LinkedHashMap<>();
    private String currentActor = "anonymous";
    public final String runTag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    public JsonNode claim;
    public String claimTitle;

    public String baseUrl() {
        return app.baseUrl();
    }

    public String email(String actor) {
        String email = ACCOUNTS.get(actor);
        if (email == null) {
            throw new IllegalArgumentException("Unknown actor " + actor);
        }
        return email;
    }

    public ApiClient api(String actor) {
        return ApiClient.signedIn(baseUrl(), email(actor), AppUnderTest.DEMO_PASSWORD);
    }

    /** Switches the active browser to the given actor's (creating it if needed). */
    public WebDriver as(String actor) {
        currentActor = actor;
        return browsers.computeIfAbsent(actor, a -> newBrowser());
    }

    public WebDriver driver() {
        return as(currentActor);
    }

    public Map<String, WebDriver> browsers() {
        return browsers;
    }

    public String currentActor() {
        return currentActor;
    }

    private static WebDriver newBrowser() {
        ChromeOptions options = new ChromeOptions();
        if (!"false".equalsIgnoreCase(System.getenv("E2E_HEADLESS"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1366,900", "--lang=en-US", "--disable-gpu", "--no-sandbox",
                "--disable-dev-shm-usage", "--disable-search-engine-choice-screen");
        options.setExperimentalOption("prefs", Map.of("intl.accept_languages", "en-US"));
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        return driver;
    }

    public void open(String path) {
        driver().get(baseUrl() + path);
    }

    public WebDriverWait waiting() {
        return new WebDriverWait(driver(), Duration.ofSeconds(15));
    }

    public WebElement byTestId(String testId) {
        return waiting().until(ExpectedConditions.visibilityOfElementLocated(testId(testId)));
    }

    public WebElement clickable(String testId) {
        return waiting().until(ExpectedConditions.elementToBeClickable(testId(testId)));
    }

    public void waitForText(String testId, String text) {
        waiting().until(ExpectedConditions.textToBePresentInElementLocated(testId(testId), text));
    }

    public static By testId(String id) {
        return By.cssSelector("[data-testid='" + id + "']");
    }

    public void signIn(String actor) {
        as(actor);
        open("/login");
        byTestId("login-email").sendKeys(email(actor));
        byTestId("login-password").sendKeys(AppUnderTest.DEMO_PASSWORD);
        clickable("login-submit").click();
        byTestId("current-user-name");
    }

    public void quitAll() {
        browsers.values().forEach(d -> {
            try {
                d.quit();
            } catch (RuntimeException ignored) {
                // browser already gone
            }
        });
        browsers.clear();
    }
}
