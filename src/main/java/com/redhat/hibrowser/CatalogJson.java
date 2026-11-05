package com.redhat.hibrowser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

final class CatalogJson {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    private CatalogJson() {
    }

    static List<ImageTag> parseTags(String json) throws IOException {
        TagsDocument document = MAPPER.readValue(json, TagsDocument.class);
        if (document.tags == null) {
            return List.of();
        }
        return List.copyOf(document.tags);
    }

    static ParsedVulnerabilities parseVulnerabilities(String json) throws IOException {
        VulnerabilityDocument document = MAPPER.readValue(json, VulnerabilityDocument.class);
        Summary summary = document.summary == null ? new Summary() : document.summary;
        return new ParsedVulnerabilities(
                document.scannedAt,
                document.scanner,
                SeverityCounts.of(summary.critical, summary.high, summary.medium, summary.low, summary.negligible, summary.unknown));
    }

    record ParsedVulnerabilities(Instant scannedAt, String scanner, SeverityCounts counts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class TagsDocument {
        public List<ImageTag> tags;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class VulnerabilityDocument {
        @JsonProperty("scanned_at")
        public Instant scannedAt;
        public String scanner;
        public Summary summary;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Summary {
        public int critical;
        public int high;
        public int medium;
        public int low;
        public int negligible;
        public int unknown;
    }
}
