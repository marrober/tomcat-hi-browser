package com.redhat.hibrowser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

final class CatalogClient implements ImageCatalog {

    private static final Logger LOG = Logger.getLogger(CatalogClient.class.getName());
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final URI apiBase;
    private final String imageName;
    private final HttpClient http;

    CatalogClient(URI apiBase, String imageName, HttpClient http) {
        this.apiBase = apiBase;
        this.imageName = imageName;
        this.http = http;
    }

    @Override
    public List<ImageTag> listTags() {
        URI uri = endpoint(apiBase, "/images/" + encode(imageName) + "/tags");
        LOG.info("Listing tags from " + uri);
        try {
            return CatalogJson.parseTags(get(uri));
        } catch (IOException ex) {
            throw new CatalogException("Could not read the tag list from " + uri, ex);
        }
    }

    @Override
    public ImageReport reportFor(ImageTag tag) {
        URI uri = endpoint(apiBase, "/images/" + encode(imageName) + "/vulnerabilities/" + encode(tag.name()));
        try {
            CatalogJson.ParsedVulnerabilities parsed = CatalogJson.parseVulnerabilities(get(uri));
            return new ImageReport(
                    tag.name(),
                    tag.variant(),
                    tag.digest(),
                    tag.pullUrl(),
                    tag.publishedAt(),
                    parsed.scannedAt(),
                    parsed.scanner(),
                    parsed.counts(),
                    null);
        } catch (RuntimeException | IOException ex) {
            LOG.log(Level.WARNING, "Vulnerability report failed for " + tag.name(), ex);
            return new ImageReport(
                    tag.name(),
                    tag.variant(),
                    tag.digest(),
                    tag.pullUrl(),
                    tag.publishedAt(),
                    null,
                    null,
                    SeverityCounts.zero(),
                    brief(ex));
        }
    }

    static URI endpoint(URI base, String path) {
        String text = base.toString();
        if (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return URI.create(text + path);
    }

    private String get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "tomcat-hi-browser/1.0")
                .GET()
                .build();
        CatalogException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status == 200) {
                    return decode(response.body());
                }
                last = new CatalogException("Catalog returned HTTP " + status + " for " + uri);
                if (status != 429 && status < 500) {
                    throw last;
                }
            } catch (IOException ex) {
                last = new CatalogException("Could not reach " + uri, ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CatalogException("Catalog request was interrupted", ex);
            }
            if (attempt == 1) {
                pauseBeforeRetry();
            }
        }
        throw last == null ? new CatalogException("Catalog request failed for " + uri) : last;
    }

    private static void pauseBeforeRetry() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CatalogException("Catalog request was interrupted", ex);
        }
    }

    private static String decode(byte[] body) throws IOException {
        if (body.length >= 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String brief(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return message.length() > 400 ? message.substring(0, 400) : message;
    }
}
