package com.vink.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the existing React/Vite frontend (built into backend/src/main/resources/static,
 * copied there from the repo's top-level `src`/`dist` build output) and falls back to
 * index.html for any unmatched path so client-side routing (React Router) keeps working,
 * the same way the original setup served a built SPA alongside the Express API.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = super.getResource(resourcePath, location);
                        if (requested != null && requested.exists()) {
                            return requested;
                        }
                        // SPA fallback - don't intercept API or health routes
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("health")) {
                            return null;
                        }
                        return new org.springframework.core.io.ClassPathResource("/static/index.html");
                    }
                });
    }
}
