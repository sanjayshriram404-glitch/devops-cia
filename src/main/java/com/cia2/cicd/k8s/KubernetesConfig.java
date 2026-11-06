package com.cia2.cicd.k8s;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

/**
 * Creates the Kubernetes API client only when the app runs inside a cluster
 * (or when K8S_DASHBOARD_ENABLED=true). Running the jar on a laptop therefore never
 * touches whatever cluster the local kubeconfig happens to point at.
 */
@Configuration
public class KubernetesConfig {

    @Bean
    public DashboardSettings dashboardSettings(
            @Value("${dashboard.k8s.enabled:auto}") String enabled,
            @Value("${POD_NAMESPACE:cicd-demo}") String namespace,
            @Value("${APP_DEPLOYMENT:cicd-demo}") String deployment,
            @Value("${dashboard.max-replicas:5}") int maxReplicas,
            @Value("${dashboard.token:}") String token) {
        boolean on = "auto".equalsIgnoreCase(enabled)
                ? System.getenv("KUBERNETES_SERVICE_HOST") != null
                : Boolean.parseBoolean(enabled);
        return new DashboardSettings(on, namespace, deployment, maxReplicas, token);
    }

    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient(DashboardSettings settings) {
        // The builder reads the in-cluster ServiceAccount token automatically.
        // When disabled we still return a client, but the service never calls it.
        return new KubernetesClientBuilder().build();
    }
}
