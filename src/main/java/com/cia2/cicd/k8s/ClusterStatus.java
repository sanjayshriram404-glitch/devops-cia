package com.cia2.cicd.k8s;

import java.util.List;

/** Snapshot of the Deployment, its pods, the Service and recent events, sent to the dashboard. */
public record ClusterStatus(
        boolean connected,
        String message,
        String namespace,
        int maxReplicas,
        boolean tokenRequired,
        DeploymentInfo deployment,
        List<PodInfo> pods,
        ServiceInfo service,
        List<EventInfo> events) {

    public static ClusterStatus disconnected(DashboardSettings s, String message) {
        return new ClusterStatus(false, message, s.namespace(), s.maxReplicas(), s.tokenRequired(),
                null, List.of(), null, List.of());
    }

    public record DeploymentInfo(String name, int desired, int ready, int available, int updated,
                                 String image, String revision, String strategy,
                                 List<String> conditions) {
    }

    public record PodInfo(String name, String status, boolean ready, int restarts, String node,
                          String ip, String startTime, String image) {
    }

    public record ServiceInfo(String name, String type, String externalIp, List<Integer> ports) {
    }

    public record EventInfo(String type, String reason, String object, String message, String time) {
    }
}
