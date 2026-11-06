package com.cia2.cicd.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

/** Runs the service against fabric8's in-memory Kubernetes API server. */
@EnableKubernetesMockClient(crud = true)
class KubernetesDashboardServiceTest {

    private static final String NS = "cicd-demo";

    KubernetesClient client; // injected by @EnableKubernetesMockClient
    private KubernetesDashboardService service;

    @BeforeEach
    void setUp() {
        service = new KubernetesDashboardService(client, new DashboardSettings(true, NS, "cicd-demo", 5, ""));
        client.apps().deployments().inNamespace(NS).resource(new DeploymentBuilder()
                .withNewMetadata().withName("cicd-demo").withNamespace(NS).endMetadata()
                .withNewSpec().withReplicas(2)
                .withNewSelector().addToMatchLabels("app", "cicd-demo").endSelector()
                .withNewTemplate().withNewMetadata().addToLabels("app", "cicd-demo").endMetadata()
                .withNewSpec().addNewContainer().withName("cicd-demo").withImage("acr.io/cicd-demo:abc1234")
                .endContainer().endSpec().endTemplate()
                .endSpec().build()).create();
        client.pods().inNamespace(NS).resource(pod("cicd-demo-1", "cicd-demo")).create();
        client.pods().inNamespace(NS).resource(pod("other-app-1", "other")).create();
    }

    @Test
    void statusListsOnlyThisAppsPods() {
        ClusterStatus s = service.status();
        assertThat(s.connected()).isTrue();
        assertThat(s.deployment().desired()).isEqualTo(2);
        assertThat(s.deployment().image()).endsWith(":abc1234");
        assertThat(s.pods()).extracting(ClusterStatus.PodInfo::name).containsExactly("cicd-demo-1");
    }

    @Test
    void scaleChangesDesiredReplicas() {
        service.scale(4);
        assertThat(client.apps().deployments().inNamespace(NS).withName("cicd-demo").get()
                .getSpec().getReplicas()).isEqualTo(4);
    }

    @Test
    void scaleRefusesMoreThanMax() {
        assertThatThrownBy(() -> service.scale(6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.scale(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletePodRefusesPodsOfOtherApps() {
        assertThatThrownBy(() -> service.deletePod("other-app-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(client.pods().inNamespace(NS).withName("other-app-1").get()).isNotNull();
    }

    @Test
    void deletePodRemovesOwnPod() {
        service.deletePod("cicd-demo-1");
        assertThat(client.pods().inNamespace(NS).withName("cicd-demo-1").get()).isNull();
    }

    @Test
    void missingDeploymentIsReportedNotThrown() {
        client.apps().deployments().inNamespace(NS).withName("cicd-demo").delete();
        ClusterStatus s = service.status();
        assertThat(s.connected()).isFalse();
        assertThat(s.message()).contains("was not found");
    }

    private static Pod pod(String name, String app) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withNamespace(NS).addToLabels("app", app).endMetadata()
                .withNewSpec().withNodeName("node-1").addNewContainer().withName("c").withImage("img")
                .endContainer().endSpec()
                .withNewStatus().withPhase("Running").withStartTime("2026-11-06T05:00:00Z")
                .addNewContainerStatus().withName("c").withReady(true).withRestartCount(0).endContainerStatus()
                .endStatus().build();
    }
}
