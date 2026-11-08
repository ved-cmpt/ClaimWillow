package com.claimwillow.e2e.support;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The disposable system under test: a Testcontainers PostgreSQL database plus the real backend jar
 * (serving the built frontend), seeded with synthetic demo accounts. Started once per test run.
 * Set {@code E2E_BASE_URL} to run against an already running instance instead (it must have been
 * seeded with {@code APP_SEED_DEMO_PASSWORD} equal to {@code E2E_DEMO_PASSWORD}).
 */
public final class AppUnderTest {

    public static final String DEMO_PASSWORD = System.getenv().getOrDefault("E2E_DEMO_PASSWORD",
            "E2e-Synthetic-Pass-2026");

    private static AppUnderTest instance;

    private final String baseUrl;
    private PostgreSQLContainer postgres;
    private Process backend;

    private AppUnderTest(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public static synchronized AppUnderTest get() {
        if (instance == null) {
            String external = System.getenv("E2E_BASE_URL");
            instance = external != null && !external.isBlank() ? new AppUnderTest(stripSlash(external)) : launch();
        }
        return instance;
    }

    public String baseUrl() {
        return baseUrl;
    }

    private static AppUnderTest launch() {
        Path jar = Path.of(System.getProperty("e2e.backendJar", "../backend/target/claimwillow.jar"));
        Path dist = Path.of(System.getProperty("e2e.frontendDist", "../frontend/dist"));
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Backend jar not found at " + jar.toAbsolutePath()
                    + ". Build it first: cd backend && ./mvnw -DskipTests package");
        }
        if (!Files.isRegularFile(dist.resolve("index.html"))) {
            throw new IllegalStateException("Frontend build not found at " + dist.toAbsolutePath()
                    + ". Build it first: cd frontend && npm ci && npm run build");
        }
        PostgreSQLContainer db = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("claimwillow_e2e");
        db.start();
        int port = freePort();
        AppUnderTest app = new AppUnderTest("http://localhost:" + port);
        app.postgres = db;
        try {
            Path logDir = Files.createDirectories(Path.of("target"));
            Path attachments = Files.createDirectories(Path.of("target", "e2e-attachments"));
            List<String> cmd = new ArrayList<>(List.of(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-jar", jar.toAbsolutePath().toString(),
                    "--server.port=" + port,
                    "--spring.datasource.url=" + db.getJdbcUrl(),
                    "--spring.datasource.username=" + db.getUsername(),
                    "--spring.datasource.password=" + db.getPassword(),
                    "--app.seed-demo-data=true",
                    "--app.seed-demo-password=" + DEMO_PASSWORD,
                    "--app.attachments.local-dir=" + attachments.toAbsolutePath(),
                    "--spring.web.resources.static-locations=" + dist.toAbsolutePath().toUri()));
            app.backend = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(logDir.resolve("e2e-backend.log").toFile())
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
            app.awaitHealthy(Duration.ofSeconds(120));
        } catch (IOException ex) {
            app.stop();
            throw new IllegalStateException("Could not start the backend", ex);
        }
        return app;
    }

    private void awaitHealthy(Duration timeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (backend != null && !backend.isAlive()) {
                throw new IllegalStateException("Backend exited early; see target/e2e-backend.log");
            }
            try {
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(baseUrl
                        + "/actuator/health")).timeout(Duration.ofSeconds(2)).build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("UP")) {
                    return;
                }
            } catch (IOException ignored) {
                // not up yet
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
            try {
                Thread.sleep(500); // polling interval while the JVM boots, not a UI wait
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
        throw new IllegalStateException("Backend did not become healthy in time; see target/e2e-backend.log");
    }

    public synchronized void stop() {
        if (backend != null && backend.isAlive()) {
            backend.destroy();
            try {
                if (!backend.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                    backend.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                backend.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static File fixture(String name) {
        return Path.of("src", "test", "resources", "fixtures", name).toAbsolutePath().toFile();
    }
}
