package com.claimwillow.e2e.steps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.claimwillow.e2e.support.World;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/** Captures a screenshot and the page URL of every open browser when a scenario fails. */
public class Hooks {

    private final World world;

    public Hooks(World world) {
        this.world = world;
    }

    @After
    public void afterScenario(Scenario scenario) throws IOException {
        try {
            if (scenario.isFailed()) {
                Path dir = Files.createDirectories(Path.of("target", "screenshots"));
                String base = scenario.getName().replaceAll("[^A-Za-z0-9]+", "-").toLowerCase();
                for (Map.Entry<String, WebDriver> e : world.browsers().entrySet()) {
                    byte[] png = ((TakesScreenshot) e.getValue()).getScreenshotAs(OutputType.BYTES);
                    Files.write(dir.resolve(base + "-" + e.getKey().toLowerCase() + ".png"), png);
                    scenario.attach(png, "image/png", e.getKey() + " browser");
                    scenario.log(e.getKey() + " was on " + e.getValue().getCurrentUrl());
                }
            }
        } finally {
            world.quitAll();
        }
    }
}
