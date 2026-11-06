package com.cia2.cicd.k8s;

/**
 * @param enabled     whether the dashboard may call the Kubernetes API
 * @param namespace   namespace the app runs in (downward API: POD_NAMESPACE)
 * @param deployment  Deployment the dashboard controls
 * @param maxReplicas upper bound for the scale buttons (keeps the 1-node cluster safe)
 * @param token       optional; when set, scale/restart/delete need header X-Dashboard-Token
 */
public record DashboardSettings(boolean enabled, String namespace, String deployment,
                                int maxReplicas, String token) {

    public boolean tokenRequired() {
        return token != null && !token.isBlank();
    }
}
