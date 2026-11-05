package com.redhat.hibrowser;

public record SeverityCounts(
        int critical,
        int high,
        int medium,
        int low,
        int negligible,
        int unknown,
        int total) {

    public static SeverityCounts zero() {
        return new SeverityCounts(0, 0, 0, 0, 0, 0, 0);
    }

    public static SeverityCounts of(int critical, int high, int medium, int low, int negligible, int unknown) {
        int total = nonNegative(critical) + nonNegative(high) + nonNegative(medium)
                + nonNegative(low) + nonNegative(negligible) + nonNegative(unknown);
        return new SeverityCounts(
                nonNegative(critical),
                nonNegative(high),
                nonNegative(medium),
                nonNegative(low),
                nonNegative(negligible),
                nonNegative(unknown),
                total);
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }
}
