package com.redhat.hibrowser;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CatalogService {

    static final String CONTEXT_ATTRIBUTE = "com.redhat.hibrowser.CatalogService";

    private static final Logger LOG = Logger.getLogger(CatalogService.class.getName());
    private static final String DEFAULT_API = "https://api-hummingbird.hummingbird-project.io/v1";

    private final ImageCatalog catalog;
    private final String repository;
    private final int tagLimit;
    private final Duration interval;
    private final boolean disabled;
    private volatile CatalogSnapshot snapshot;
    private ScheduledExecutorService executor;

    CatalogService(ImageCatalog catalog, String repository, int tagLimit, Duration interval) {
        this(catalog, repository, tagLimit, interval, false);
    }

    private CatalogService(ImageCatalog catalog, String repository, int tagLimit, Duration interval, boolean disabled) {
        this.catalog = catalog;
        this.repository = repository;
        this.tagLimit = tagLimit;
        this.interval = interval;
        this.disabled = disabled;
        this.snapshot = CatalogSnapshot.loading(repository, refreshMinutes());
    }

    public static CatalogService fromEnvironment() {
        String api = env("HIBROWSER_API_BASE", DEFAULT_API);
        String image = env("HIBROWSER_IMAGE", "tomcat");
        String repository = env("HIBROWSER_REPOSITORY", "registry.access.redhat.com/hi/" + image);
        int limit = positiveInt("HIBROWSER_TAG_LIMIT", env("HIBROWSER_TAG_LIMIT", "5"), 5);
        int minutes = positiveInt("HIBROWSER_REFRESH_MINUTES", env("HIBROWSER_REFRESH_MINUTES", "60"), 60);
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        ImageCatalog catalog = new CatalogClient(URI.create(stripTrailingSlash(api)), image, http);
        return new CatalogService(catalog, repository, limit, Duration.ofMinutes(minutes));
    }

    static CatalogService unavailable(String message) {
        CatalogService service = new CatalogService(
                null,
                "registry.access.redhat.com/hi/tomcat",
                5,
                Duration.ofHours(1),
                true);
        service.snapshot = CatalogSnapshot.failed(service.repository, 60, message);
        return service;
    }

    public void start() {
        if (disabled) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "hibrowser-refresh");
            thread.setDaemon(true);
            return thread;
        });
        long minutes = Math.max(1, interval.toMinutes());
        executor.scheduleWithFixedDelay(this::refreshQuietly, 0, minutes, TimeUnit.MINUTES);
    }

    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public CatalogSnapshot snapshot() {
        return snapshot;
    }

    public synchronized void refresh() {
        if (disabled) {
            return;
        }
        int minutes = refreshMinutes();
        try {
            List<ImageTag> selected = TagSelector.latest(catalog.listTags(), tagLimit);
            List<ImageReport> reports = new ArrayList<>(selected.size());
            for (ImageTag tag : selected) {
                reports.add(catalog.reportFor(tag));
            }
            snapshot = CatalogSnapshot.ready(repository, minutes, Instant.now(), reports);
            LOG.info("Stored vulnerability reports for " + reports.size() + " tags of " + repository);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Tag refresh failed", ex);
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            CatalogSnapshot previous = snapshot;
            Instant next = Instant.now().plus(interval);
            if (previous != null && !previous.images().isEmpty()) {
                snapshot = previous.withRetainedFailure(message, next);
            } else {
                snapshot = CatalogSnapshot.failed(repository, minutes, message);
            }
        }
    }

    private void refreshQuietly() {
        try {
            refresh();
        } catch (Throwable ex) {
            LOG.log(Level.SEVERE, "Uncaught refresh failure", ex);
        }
    }

    private int refreshMinutes() {
        long minutes = interval.toMinutes();
        if (minutes < 1 || minutes > Integer.MAX_VALUE) {
            return 60;
        }
        return (int) minutes;
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static int positiveInt(String name, String raw, int defaultValue) {
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ex) {
            LOG.warning(name + " must be a positive integer. Using " + defaultValue + ".");
            return defaultValue;
        }
        LOG.warning(name + " must be a positive integer. Using " + defaultValue + ".");
        return defaultValue;
    }

    private static String stripTrailingSlash(String value) {
        String text = value;
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
