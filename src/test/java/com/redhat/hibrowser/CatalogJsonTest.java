package com.redhat.hibrowser;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogJsonTest {

    @Test
    void parsesTagsAndIgnoresFloatingMetadata() throws Exception {
        String json = """
                {
                  "tags": [
                    {
                      "name": "11.0.27",
                      "canonical": "11.0.27",
                      "variant": "default",
                      "pull_url": "registry.access.redhat.com/hi/tomcat:11.0.27",
                      "digest": "sha256:abc",
                      "sizes": {"amd64": 10},
                      "oldest_created": "2026-10-25T05:52:25Z",
                      "deprecated": false,
                      "superseded": false
                    }
                  ],
                  "variants": [{"name": "default"}]
                }
                """;

        List<ImageTag> tags = CatalogJson.parseTags(json);

        assertEquals(1, tags.size());
        ImageTag tag = tags.get(0);
        assertEquals("11.0.27", tag.name());
        assertEquals("sha256:abc", tag.digest());
        assertEquals(Instant.parse("2026-10-25T05:52:25Z"), tag.publishedAt());
    }

    @Test
    void parsesSeveritySummaryAndIgnoresCveDetails() throws Exception {
        String json = """
                {
                  "scanned_at": "2026-11-05T07:50:00.426299589Z",
                  "scanner": "grype 0.121.1",
                  "summary": {
                    "total": 46,
                    "critical": 0,
                    "high": 2,
                    "medium": 30,
                    "low": 14,
                    "negligible": 0,
                    "unknown": 0
                  },
                  "vulnerabilities": [
                    {"id": "CVE-2026-119586", "severity": "High", "description": "ignored"}
                  ]
                }
                """;

        CatalogJson.ParsedVulnerabilities parsed = CatalogJson.parseVulnerabilities(json);

        assertEquals("grype 0.121.1", parsed.scanner());
        assertEquals(Instant.parse("2026-11-05T07:50:00.426299589Z"), parsed.scannedAt());
        assertEquals(new SeverityCounts(0, 2, 30, 14, 0, 0, 46), parsed.counts());
    }

    @Test
    void joinsApiBaseWithoutDroppingTheVersionSegment() {
        URI uri = CatalogClient.endpoint(
                URI.create("https://api-hummingbird.hummingbird-project.io/v1"),
                "/images/tomcat/vulnerabilities/11.0.27");

        assertEquals(
                "https://api-hummingbird.hummingbird-project.io/v1/images/tomcat/vulnerabilities/11.0.27",
                uri.toString());
        assertNull(uri.getQuery());
        assertTrue(uri.getPath().endsWith("/11.0.27"));
    }
}
