package eu.xenit.alfred.content.gateway.servicediscovery;

public record AppService(String name, String appId, String deploymentId, String policyPackage, String namespace) {
    // Since this is a kubernetes service, the name is guaranteed to be unique and thereby suitable as id
    public String id() {
        return name();
    }

    public String serviceUrl() {
        return "http://%s.%s.svc.cluster.local:8080".formatted(name, namespace);
    }

    public String hostname() {
        return this.appId() + ".userapps.contentgrid.com";
    }

}
