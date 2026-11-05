package com.cia2.cicd;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppInfoServiceTest {

    private final AppInfoService service =
            new AppInfoService("2.0.0", "hello", "abc123", "pod-1");

    @Test
    void greetsByName() {
        assertThat(service.greet("Sanjay")).isEqualTo("Hello, Sanjay! Served by cicd-demo v2.0.0");
    }

    @Test
    void trimsName() {
        assertThat(service.greet("  Ana  ")).startsWith("Hello, Ana!");
    }

    @Test
    void blankNameFallsBackToDefault() {
        assertThat(service.greet("  ")).startsWith("Hello, DevOps Engineer!");
        assertThat(service.greet(null)).startsWith("Hello, DevOps Engineer!");
    }

    @Test
    void infoMapContainsBuildMetadata() {
        assertThat(service.asMap())
                .containsEntry("version", "2.0.0")
                .containsEntry("buildSha", "abc123")
                .containsEntry("pod", "pod-1");
    }
}
