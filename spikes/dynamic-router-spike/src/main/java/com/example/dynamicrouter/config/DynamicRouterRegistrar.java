package com.example.dynamicrouter.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public class DynamicRouterRegistrar {

    private final RequestMappingHandlerMapping handlerMapping;

    public DynamicRouterRegistrar(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    public void registerRoute(String path) throws Exception {
        RequestMappingInfo mappingInfo = RequestMappingInfo
                .paths(path)
                .methods(RequestMethod.GET)
                .build();

        DynamicRouteHandler handler = new DynamicRouteHandler();
        handlerMapping.registerMapping(mappingInfo, handler,
                DynamicRouteHandler.class.getMethod("handle"));
    }

    public static class DynamicRouteHandler {
        public ResponseEntity<String> handle() {
            return ResponseEntity.ok("Hello from dynamic route");
        }
    }
}
