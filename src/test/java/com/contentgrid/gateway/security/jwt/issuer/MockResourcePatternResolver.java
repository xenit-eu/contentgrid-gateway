package com.contentgrid.gateway.security.jwt.issuer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

@RequiredArgsConstructor
@Builder
class MockResourcePatternResolver implements ResourcePatternResolver {

    @Singular
    private final Map<String, Resource> resources;

    private final PathMatcher pathMatcher = new AntPathMatcher();


    @Override
    public Resource getResource(String location) {
        return resources.getOrDefault(location, new NonExistingResource(location));
    }

    @Override
    public ClassLoader getClassLoader() {
        return null;
    }

    @Override
    public Resource[] getResources(String locationPattern) throws IOException {
        return resources.keySet()
                .stream()
                .filter(path -> pathMatcher.match(locationPattern, path))
                .map(this::getResource)
                .toArray(Resource[]::new);
    }

    @RequiredArgsConstructor
    private static class NonExistingResource extends AbstractResource {

        private final String path;

        @Override
        public String getDescription() {
            return "NonExistingResource [%s]".formatted(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new FileNotFoundException(getDescription() + " can not be opened because it does not exist");
        }

        @Override
        public boolean exists() {
            return false;
        }
    }
}