package com.redhat.hibrowser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageTag(
        String name,
        String canonical,
        String variant,
        String digest,
        @JsonProperty("pull_url") String pullUrl,
        @JsonProperty("oldest_created") Instant publishedAt,
        boolean deprecated,
        boolean superseded) {
}
