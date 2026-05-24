package com.example.dynamicrouter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
public class DynamicRouterConfig {

    @Bean
    public DynamicRouterRegistrar dynamicRouterRegistrar(RequestMappingHandlerMapping handlerMapping) {
        return new DynamicRouterRegistrar(handlerMapping);
    }
}
