package com.contentgrid.gateway.runtime.jwt.issuer;

import com.contentgrid.gateway.security.jwt.issuer.JwtClaimsResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

@AllArgsConstructor
@RequiredArgsConstructor
public class JwtClaimsResolverLocator implements ApplicationContextAware {
    private Map<String, Supplier<JwtClaimsResolver>> mapping;
    private final Map<String, JwtClaimsResolver> initialized = new HashMap<>();

    public Optional<JwtClaimsResolver> findClaimsResolver(String resolverName) {
        return Optional.ofNullable(initialized.get(resolverName))
                .or(() -> resolveFromMapping(resolverName));
    }

    private Optional<JwtClaimsResolver> resolveFromMapping(String resolverName) {
        var maybeClaimsResolver =  Optional.ofNullable(mapping.get(resolverName))
                .map(Supplier::get);

        maybeClaimsResolver.ifPresent(claimsResolver -> initialized.put(resolverName, claimsResolver));

        return maybeClaimsResolver;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        mapping = new HashMap<>();
        Map<String, String> beanNameMapping = new HashMap<>();

        for (String beanName : applicationContext.getBeanNamesForType(JwtClaimsResolver.class)) {
            var annotation = applicationContext.findAnnotationOnBean(beanName, NamedJwtClaimsResolver.class);
            if(annotation != null) {
                var previousBeanName = beanNameMapping.putIfAbsent(annotation.value(), beanName);
                if(previousBeanName != null) {
                    throw new IllegalStateException("Multiple JwtClaimsResolver are registered with the same name '%s': beans '%s' and %s'".formatted(
                            annotation.value(),
                            previousBeanName,
                            beanName
                    ));
                }
                mapping.putIfAbsent(annotation.value(), () -> applicationContext.getBean(beanName, JwtClaimsResolver.class));
            }
        }

    }
}
