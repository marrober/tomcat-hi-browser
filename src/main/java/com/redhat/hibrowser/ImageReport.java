package com.redhat.hibrowser;

import java.time.Instant;

public record ImageReport(
        String tag,
        String variant,
        String digest,
        String pullUrl,
        Instant publishedAt,
        Instant scannedAt,
        String scanner,
        SeverityCounts counts,
        String error) {

    public ImageReport {
        counts = counts == null ? SeverityCounts.zero() : counts;
    }
}
