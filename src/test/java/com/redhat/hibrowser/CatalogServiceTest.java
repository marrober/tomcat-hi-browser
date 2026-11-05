package com.redhat.hibrowser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogServiceTest {

    @Test
    void keepsThePreviousReportWhenALaterRefreshFails() {
        ImageTag tag = new ImageTag(
                "11.0.27",
                "11.0.27",
                "default",
                "sha256:abc",
                "registry.access.redhat.com/hi/tomcat:11.0.27",
                Instant.parse("2026-10-25T05:52:25Z"),
                false,
                false);
        ImageCatalog catalog = new ImageCatalog() {
            private int calls;

            @Override
            public List<ImageTag> listTags() {
                calls++;
                if (calls > 1) {
                    throw new CatalogException("catalog unavailable");
                }
                return List.of(tag);
            }

            @Override
            public ImageReport reportFor(ImageTag imageTag) {
                return new ImageReport(
                        imageTag.name(),
                        imageTag.variant(),
                        imageTag.digest(),
                        imageTag.pullUrl(),
                        imageTag.publishedAt(),
                        Instant.parse("2026-11-05T07:50:00Z"),
                        "grype 0.121.1",
                        SeverityCounts.of(0, 2, 30, 14, 0, 0),
                        null);
            }
        };

        CatalogService service = new CatalogService(
                catalog,
                "registry.access.redhat.com/hi/tomcat",
                5,
                Duration.ofHours(1));

        assertEquals("loading", service.snapshot().status());
        service.refresh();
        service.refresh();

        CatalogSnapshot snapshot = service.snapshot();
        assertEquals("stale", snapshot.status());
        assertEquals("catalog unavailable", snapshot.error());
        assertEquals(1, snapshot.images().size());
        assertEquals(46, snapshot.images().get(0).counts().total());
        assertNotNull(snapshot.refreshedAt());
    }

    @Test
    void liveCatalogReturnsFiveVersionTags() {
        Assumptions.assumeTrue(Boolean.getBoolean("hibrowser.live"));
        CatalogService service = CatalogService.fromEnvironment();
        service.refresh();

        CatalogSnapshot snapshot = service.snapshot();
        assertEquals("ok", snapshot.status(), snapshot.error());
        assertEquals("registry.access.redhat.com/hi/tomcat", snapshot.repository());
        assertEquals(5, snapshot.images().size());
        assertNull(snapshot.error());

        Instant previous = Instant.MAX;
        for (ImageReport image : snapshot.images()) {
            assertNull(image.error(), image.tag() + " " + image.error());
            assertTrue(image.tag().matches("\\d+\\.\\d+\\.\\d+"), image.tag());
            assertFalse(image.tag().toLowerCase().contains("jdk") || image.tag().toLowerCase().contains("builder"));
            assertFalse(image.publishedAt().isAfter(previous), image.tag());
            assertTrue(image.counts().total() >= 0);
            previous = image.publishedAt();
        }
    }
}
