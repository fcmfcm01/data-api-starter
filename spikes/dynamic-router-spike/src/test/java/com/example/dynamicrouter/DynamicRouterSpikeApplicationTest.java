package com.example.dynamicrouter;

import com.example.dynamicrouter.config.DynamicRouterRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DynamicRouterSpikeApplicationTest {

    @Autowired
    private DynamicRouterRegistrar dynamicRouterRegistrar;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        assertNotNull(dynamicRouterRegistrar, "DynamicRouterRegistrar should be available");
    }

    @Test
    void shouldRegisterDynamicRouteAndReturnGreeting() throws Exception {
        String dynamicPath = "/api/dynamic/hello";

        dynamicRouterRegistrar.registerRoute(dynamicPath);

        ResponseEntity<String> response = restTemplate.getForEntity(dynamicPath, String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("Hello from dynamic route", response.getBody());
    }

    @Test
    void shouldRegisterMultipleDynamicRoutes() throws Exception {
        String dynamicPath = "/api/dynamic/conflict-test";

        dynamicRouterRegistrar.registerRoute(dynamicPath);

        ResponseEntity<String> response = restTemplate.getForEntity(dynamicPath, String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("Hello from dynamic route", response.getBody());
    }
}
