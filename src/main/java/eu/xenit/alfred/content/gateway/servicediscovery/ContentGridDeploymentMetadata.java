package eu.xenit.alfred.content.gateway.servicediscovery;

import java.util.Optional;
import org.springframework.cloud.client.ServiceInstance;

public interface ContentGridDeploymentMetadata {

    Optional<String> getApplicationId(ServiceInstance service);

    Optional<String> getPolicyPackage(ServiceInstance service);


}
