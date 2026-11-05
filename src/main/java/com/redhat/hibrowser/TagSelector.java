package com.redhat.hibrowser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Picks the newest plain release tags, such as {@code 10.1.60} and {@code 11.0.27}.
 * Variant tags that mention JDK or builder are omitted, as are floating aliases
 * such as {@code latest} and {@code 11}. Older patch releases stay eligible after
 * the catalog marks them superseded.
 */
public final class TagSelector {

    private static final Pattern PLAIN_RELEASE = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    private TagSelector() {
    }

    public static List<ImageTag> latest(List<ImageTag> tags, int limit) {
        if (tags == null || limit < 1) {
            return List.of();
        }
        List<ImageTag> ordered = tags.stream()
                .filter(tag -> tag != null && isPlainRelease(tag.name()))
                .filter(tag -> tag.name().equals(tag.canonical()))
                .filter(tag -> !tag.deprecated())
                .filter(tag -> hasText(tag.digest()))
                .sorted(Comparator
                        .comparing(ImageTag::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ImageTag::name, Comparator.reverseOrder()))
                .toList();

        Set<String> seenDigests = new HashSet<>();
        List<ImageTag> selected = new ArrayList<>();
        for (ImageTag tag : ordered) {
            if (seenDigests.add(tag.digest())) {
                selected.add(tag);
                if (selected.size() == limit) {
                    break;
                }
            }
        }
        return List.copyOf(selected);
    }

    private static boolean isPlainRelease(String name) {
        if (!hasText(name)) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("builder") || lower.contains("jdk")) {
            return false;
        }
        return PLAIN_RELEASE.matcher(name).matches();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
