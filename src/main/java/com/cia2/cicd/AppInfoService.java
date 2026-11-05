package com.cia2.cicd;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Holds the build/deployment metadata shown on the home page and returned by /api/info.
 * BUILD_SHA and POD_NAME are injected by the pipeline and Kubernetes, so the page proves
 * which commit is running and which pod served the request.
 */
@Service
public class AppInfoService {

    private final String version;
    private final String message;
    private final String buildSha;
    private final String podName;

    public AppInfoService(@Value("${app.version}") String version,
                          @Value("${app.message}") String message,
                          @Value("${BUILD_SHA:local}") String buildSha,
                          @Value("${POD_NAME:localhost}") String podName) {
        this.version = version;
        this.message = message;
        this.buildSha = buildSha;
        this.podName = podName;
    }

    public String getVersion() {
        return version;
    }

    public String getMessage() {
        return message;
    }

    public String getBuildSha() {
        return buildSha;
    }

    public String getPodName() {
        return podName;
    }

    public Map<String, String> asMap() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("application", "cicd-demo");
        info.put("version", version);
        info.put("message", message);
        info.put("buildSha", buildSha);
        info.put("pod", podName);
        return info;
    }

    /** Builds a greeting; blank or missing names fall back to "DevOps Engineer". */
    public String greet(String name) {
        String who = (name == null || name.isBlank()) ? "DevOps Engineer" : name.trim();
        return "Hello, " + who + "! Served by cicd-demo v" + version;
    }
}
