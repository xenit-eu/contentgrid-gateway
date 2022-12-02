package eu.xenit.alfred.content.gateway.servicediscovery;

public record AppService(String id, String name, String namespace) {
    public String getServiceUrl() {
        return "http://%s.%s.svc.cluster.local:8080".formatted(name, namespace);
    }
}
