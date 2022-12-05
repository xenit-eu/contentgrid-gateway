package eu.xenit.alfred.content.gateway.servicediscovery;

public record AppService(String id, String deploymentId, String name, String namespace) {
    public String getServiceUrl() {
        return "http://%s.%s.svc.cluster.local:8080".formatted(name, namespace);
    }

    public String getSafeDeploymentId() {
        return makeSafeId("deployment", this.deploymentId());
    }

    public String getSafeApplicationId() {
        return makeSafeId("application", this.id());
    }

    private String makeSafeId(String prefix, String id) {
        return prefix + id.replaceAll("[^A-Za-z\\d]", "");
    }
}
