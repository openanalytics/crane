package eu.openanalytics.rdepot.crane.model;

import java.time.Duration;

public class CacheRule {

    private String pattern;
    private Duration maxAge;

    public Duration getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(String maxAge) {
        System.out.println(maxAge);
    }

    public void setMaxAge(Duration maxAge) {
        if (maxAge.isNegative()) {
            throw new IllegalArgumentException("Incorrect configuration detected: the maxAge of a cache entry must be positive");
        }
        // allow up to one year ( https://stackoverflow.com/a/25201898/1393103 )
        if (maxAge.compareTo(Duration.ofDays(365)) > 0) {
            throw new IllegalArgumentException("Incorrect configuration detected: the maxAge of a cache entry may not be greater than one year");
        }
        this.maxAge = maxAge;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        if (!pattern.startsWith("/")) {
            throw new IllegalArgumentException("Incorrect configuration detected: the pattern of a cache entry must start with /");
        }
        this.pattern = pattern;
    }

}
