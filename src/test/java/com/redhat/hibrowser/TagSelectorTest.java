package com.redhat.hibrowser;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagSelectorTest {

    @Test
    void keepsFiveNewestPlainReleasesAndSkipsVariantTags() {
        List<ImageTag> tags = List.of(
                tag("latest", "11.0.27", "2026-10-25T05:52:25Z", "sha-runtime-11"),
                tag("11", "11.0.27", "2026-10-25T05:52:25Z", "sha-runtime-11"),
                tag("11.0.27", "11.0.27", "2026-10-25T05:52:25Z", "sha-runtime-11"),
                tag("11.0.26", "11.0.26", "2026-10-12T11:51:07Z", false, true, "sha-11-26"),
                tag("11.0.27-jdk21", "11.0.27-jdk21", "2026-10-25T05:52:25Z", "sha-jdk-11"),
                tag("11.0.27-builder", "11.0.27-builder", "2026-11-03T05:46:11Z", "sha-builder-11"),
                tag("10.1.61", "10.1.61", "2026-11-03T19:04:48Z", "sha-runtime-10"),
                tag("10.1.60", "10.1.60", "2026-10-19T20:04:57Z", false, true, "sha-10-60"),
                tag("10.1.59", "10.1.59", "2026-09-17T01:45:42Z", false, true, "sha-10-59"),
                tag("10.1.61-jdk21", "10.1.61-jdk21", "2026-11-03T19:04:48Z", "sha-jdk-10"),
                tag("10.1.61-builder", "10.1.61-builder", "2026-11-04T01:49:29Z", "sha-builder-10"),
                tag("10.1.61-jdk21-builder", "10.1.61-jdk21-builder", "2026-11-04T01:49:29Z", "sha-jdk-builder-10"),
                tag("10.1.50", "10.1.50", "2026-09-01T00:00:00Z", "sha-old"),
                tag("9.0.90", "9.0.90", "2026-08-01T00:00:00Z", true, false, "sha-deprecated"));

        List<ImageTag> selected = TagSelector.latest(tags, 5);

        assertEquals(
                List.of("10.1.61", "11.0.27", "10.1.60", "11.0.26", "10.1.59"),
                selected.stream().map(ImageTag::name).toList());
        assertTrue(selected.stream().noneMatch(tag -> tag.name().contains("jdk") || tag.name().contains("builder")));
    }

    @Test
    void dropsDuplicateDigestsAndKeepsSupersededPlainReleases() {
        ImageTag current = tag("11.0.27", "11.0.27", "2026-10-25T05:52:25Z", "sha-same");
        ImageTag sameDigestLater = tag("11.0.27", "11.0.27", "2026-10-26T00:00:00Z", "sha-same");
        ImageTag superseded = tag("11.0.26", "11.0.26", "2026-10-12T11:51:07Z", false, true, "sha-26");
        ImageTag jdk = tag("11.0.27-jdk21", "11.0.27-jdk21", "2026-11-04T00:00:00Z", "sha-jdk");

        List<ImageTag> selected = TagSelector.latest(List.of(current, sameDigestLater, superseded, jdk), 5);

        assertEquals(List.of("11.0.27", "11.0.26"), selected.stream().map(ImageTag::name).toList());
        assertTrue(selected.stream().anyMatch(ImageTag::superseded));
    }

    private static ImageTag tag(String name, String canonical, String publishedAt, String digest) {
        return tag(name, canonical, publishedAt, false, false, digest);
    }

    private static ImageTag tag(String name, String canonical, String publishedAt, boolean deprecated, boolean superseded, String digest) {
        return new ImageTag(
                name,
                canonical,
                "default",
                digest,
                "registry.access.redhat.com/hi/tomcat:" + name,
                Instant.parse(publishedAt),
                deprecated,
                superseded);
    }
}
