package com.cia2.cicd.k8s;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

/** Reads and changes the app's own Deployment through the Kubernetes API. */
@Service
public class KubernetesDashboardService {

    private static final Logger log = LoggerFactory.getLogger(KubernetesDashboardService.class);
    private static final int MAX_EVENTS = 12;

    private final KubernetesClient client;
    private final DashboardSettings settings;

    public KubernetesDashboardService(KubernetesClient client, DashboardSettings settings) {
        this.client = client;
        this.settings = settings;
    }

    public DashboardSettings settings() {
        return settings;
    }

    public ClusterStatus status() {
        if (!settings.enabled()) {
            return ClusterStatus.disconnected(settings,
                    "Not running inside Kubernetes. Deploy to the cluster to see live pods.");
        }
        try {
            Deployment d = deployment();
            if (d == null) {
                return ClusterStatus.disconnected(settings,
                        "Deployment " + settings.deployment() + " was not found in namespace " + settings.namespace() + ".");
            }
            return new ClusterStatus(true, "ok", settings.namespace(), settings.maxReplicas(),
                    settings.tokenRequired(), toInfo(d), pods(), service(), events());
        } catch (KubernetesClientException e) {
            log.warn("Kubernetes API call failed: {}", e.getMessage());
            return ClusterStatus.disconnected(settings, "Kubernetes API error " + e.getCode() + ": " + shortMessage(e));
        }
    }

    /** Sets the replica count; refuses values outside 1..maxReplicas. */
    public int scale(int replicas) {
        if (replicas < 1 || replicas > settings.maxReplicas()) {
            throw new IllegalArgumentException("Replicas must be between 1 and " + settings.maxReplicas() + ".");
        }
        requireEnabled();
        client.apps().deployments().inNamespace(settings.namespace())
                .withName(settings.deployment()).scale(replicas);
        log.info("Scaled {} to {} replicas", settings.deployment(), replicas);
        return replicas;
    }

    /** Same as `kubectl rollout restart`: replaces every pod one at a time. */
    public void restart() {
        requireEnabled();
        client.apps().deployments().inNamespace(settings.namespace())
                .withName(settings.deployment()).rolling().restart();
        log.info("Rollout restart of {}", settings.deployment());
    }

    /** Deletes one pod of this app so the Deployment's self-healing can be watched. */
    public void deletePod(String podName) {
        requireEnabled();
        Pod pod = client.pods().inNamespace(settings.namespace()).withName(podName).get();
        if (pod == null || !settings.deployment().equals(label(pod, "app"))) {
            throw new IllegalArgumentException("Pod " + podName + " is not part of " + settings.deployment() + ".");
        }
        client.pods().inNamespace(settings.namespace()).withName(podName).delete();
        log.info("Deleted pod {}", podName);
    }

    private void requireEnabled() {
        if (!settings.enabled()) {
            throw new IllegalStateException("Not running inside Kubernetes.");
        }
    }

    private Deployment deployment() {
        return client.apps().deployments().inNamespace(settings.namespace())
                .withName(settings.deployment()).get();
    }

    private ClusterStatus.DeploymentInfo toInfo(Deployment d) {
        DeploymentStatus st = d.getStatus();
        Map<String, String> ann = d.getMetadata().getAnnotations();
        List<String> conditions = st == null || st.getConditions() == null ? List.of()
                : st.getConditions().stream()
                        .map(c -> c.getType() + "=" + c.getStatus())
                        .toList();
        return new ClusterStatus.DeploymentInfo(
                d.getMetadata().getName(),
                nz(d.getSpec().getReplicas()),
                st == null ? 0 : nz(st.getReadyReplicas()),
                st == null ? 0 : nz(st.getAvailableReplicas()),
                st == null ? 0 : nz(st.getUpdatedReplicas()),
                d.getSpec().getTemplate().getSpec().getContainers().get(0).getImage(),
                ann == null ? "" : ann.getOrDefault("deployment.kubernetes.io/revision", ""),
                d.getSpec().getStrategy() == null ? "" : d.getSpec().getStrategy().getType(),
                conditions);
    }

    private List<ClusterStatus.PodInfo> pods() {
        return client.pods().inNamespace(settings.namespace())
                .withLabel("app", settings.deployment()).list().getItems().stream()
                .map(this::toInfo)
                .sorted(Comparator.comparing(p -> Objects.toString(p.startTime(), "")))
                .toList();
    }

    private ClusterStatus.PodInfo toInfo(Pod p) {
        List<ContainerStatus> cs = p.getStatus() == null || p.getStatus().getContainerStatuses() == null
                ? List.of() : p.getStatus().getContainerStatuses();
        boolean ready = !cs.isEmpty() && cs.stream().allMatch(c -> Boolean.TRUE.equals(c.getReady()));
        int restarts = cs.stream().mapToInt(c -> nz(c.getRestartCount())).sum();
        return new ClusterStatus.PodInfo(
                p.getMetadata().getName(),
                podStatus(p, cs, ready),
                ready,
                restarts,
                p.getSpec().getNodeName(),
                p.getStatus() == null ? null : p.getStatus().getPodIP(),
                p.getStatus() == null ? null : p.getStatus().getStartTime(),
                p.getSpec().getContainers().get(0).getImage());
    }

    /** Mirrors the STATUS column of `kubectl get pods`. */
    static String podStatus(Pod p, List<ContainerStatus> cs, boolean ready) {
        if (p.getMetadata().getDeletionTimestamp() != null) {
            return "Terminating";
        }
        for (ContainerStatus c : cs) {
            if (c.getState() != null && c.getState().getWaiting() != null) {
                return c.getState().getWaiting().getReason(); // ContainerCreating, CrashLoopBackOff, ...
            }
        }
        String phase = p.getStatus() == null ? "Pending" : p.getStatus().getPhase();
        if ("Running".equals(phase) && !ready) {
            return "Starting";
        }
        return phase;
    }

    private ClusterStatus.ServiceInfo service() {
        io.fabric8.kubernetes.api.model.Service s = client.services().inNamespace(settings.namespace())
                .withName(settings.deployment()).get();
        if (s == null) {
            return null;
        }
        String ip = null;
        if (s.getStatus() != null && s.getStatus().getLoadBalancer() != null
                && s.getStatus().getLoadBalancer().getIngress() != null
                && !s.getStatus().getLoadBalancer().getIngress().isEmpty()) {
            LoadBalancerIngress in = s.getStatus().getLoadBalancer().getIngress().get(0);
            ip = in.getIp() != null ? in.getIp() : in.getHostname();
        }
        List<Integer> ports = s.getSpec().getPorts().stream().map(ServicePort::getPort).toList();
        return new ClusterStatus.ServiceInfo(s.getMetadata().getName(), s.getSpec().getType(), ip, ports);
    }

    private List<ClusterStatus.EventInfo> events() {
        try {
            return client.v1().events().inNamespace(settings.namespace()).list().getItems().stream()
                    .filter(e -> e.getInvolvedObject() != null && e.getInvolvedObject().getName() != null
                            && e.getInvolvedObject().getName().startsWith(settings.deployment()))
                    .sorted(Comparator.comparing(KubernetesDashboardService::eventTime).reversed())
                    .limit(MAX_EVENTS)
                    .map(e -> new ClusterStatus.EventInfo(e.getType(), e.getReason(),
                            e.getInvolvedObject().getKind() + "/" + e.getInvolvedObject().getName(),
                            e.getMessage(), eventTime(e)))
                    .toList();
        } catch (KubernetesClientException e) {
            return List.of(); // events are optional; missing RBAC must not break the page
        }
    }

    private static String eventTime(Event e) {
        if (e.getLastTimestamp() != null) {
            return e.getLastTimestamp();
        }
        if (e.getEventTime() != null && e.getEventTime().getTime() != null) {
            return e.getEventTime().getTime();
        }
        return Objects.toString(e.getMetadata().getCreationTimestamp(), "");
    }

    private static String label(Pod p, String key) {
        Map<String, String> labels = p.getMetadata().getLabels();
        return labels == null ? null : labels.get(key);
    }

    private static String shortMessage(KubernetesClientException e) {
        return e.getStatus() != null && e.getStatus().getMessage() != null
                ? e.getStatus().getMessage() : e.getMessage();
    }

    private static int nz(Integer i) {
        return i == null ? 0 : i;
    }
}
