package com.cia2.cicd.k8s;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.fabric8.kubernetes.client.KubernetesClientException;

/** JSON API behind the dashboard. Changes are refused unless the app runs in Kubernetes. */
@RestController
@RequestMapping("/api/k8s")
public class DashboardController {

    private final KubernetesDashboardService k8s;

    public DashboardController(KubernetesDashboardService k8s) {
        this.k8s = k8s;
    }

    @GetMapping("/status")
    public ClusterStatus status() {
        return k8s.status();
    }

    @PostMapping("/scale")
    public Map<String, Object> scale(@RequestBody Map<String, Integer> body,
                                     @RequestHeader(value = "X-Dashboard-Token", required = false) String token) {
        checkToken(token);
        Integer replicas = body.get("replicas");
        if (replicas == null) {
            throw new IllegalArgumentException("Send {\"replicas\": <number>}.");
        }
        return Map.of("replicas", k8s.scale(replicas));
    }

    @PostMapping("/restart")
    public Map<String, Object> restart(@RequestHeader(value = "X-Dashboard-Token", required = false) String token) {
        checkToken(token);
        k8s.restart();
        return Map.of("restarted", true);
    }

    @DeleteMapping("/pods/{name}")
    public Map<String, Object> deletePod(@PathVariable String name,
                                         @RequestHeader(value = "X-Dashboard-Token", required = false) String token) {
        checkToken(token);
        k8s.deletePod(name);
        return Map.of("deleted", name);
    }

    private void checkToken(String supplied) {
        DashboardSettings s = k8s.settings();
        if (!s.tokenRequired()) {
            return;
        }
        byte[] expected = s.token().getBytes(StandardCharsets.UTF_8);
        byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new TokenException();
        }
    }

    static class TokenException extends RuntimeException {
    }

    @ExceptionHandler(TokenException.class)
    ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "This action needs the dashboard token."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> unavailable(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(KubernetesClientException.class)
    ResponseEntity<Map<String, String>> apiError(KubernetesClientException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Kubernetes refused the request (" + e.getCode() + ")."));
    }
}
