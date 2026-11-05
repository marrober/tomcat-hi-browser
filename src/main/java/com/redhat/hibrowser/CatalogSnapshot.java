package com.redhat.hibrowser;

import java.time.Instant;
import java.util.List;

public record CatalogSnapshot(
        String repository,
        String status,
        int refreshMinutes,
        Instant refreshedAt,
        Instant nextRefreshAt,
        String error,
        List<ImageReport> images) {

    public CatalogSnapshot {
        images = images == null ? List.of() : List.copyOf(images);
    }

    static CatalogSnapshot loading(String repository, int refreshMinutes) {
        return new CatalogSnapshot(repository, "loading", refreshMinutes, null, null, null, List.of());
    }

    static CatalogSnapshot ready(String repository, int refreshMinutes, Instant refreshedAt, List<ImageReport> images) {
        return new CatalogSnapshot(
                repository,
                "ok",
                refreshMinutes,
                refreshedAt,
                refreshedAt.plusSeconds(refreshMinutes * 60L),
                null,
                images);
    }

    static CatalogSnapshot failed(String repository, int refreshMinutes, String error) {
        return new CatalogSnapshot(repository, "error", refreshMinutes, null, null, error, List.of());
    }

    CatalogSnapshot withRetainedFailure(String error, Instant nextRefreshAt) {
        return new CatalogSnapshot(repository, "stale", refreshMinutes, refreshedAt, nextRefreshAt, error, images);
    }
}
