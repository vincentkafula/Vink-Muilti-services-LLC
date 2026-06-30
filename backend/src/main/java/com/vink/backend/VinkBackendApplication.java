package com.vink.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Java/Spring Boot port of the VINK Multi Services LLC platform backend
 * (originally server/src/index.ts on Express). Serves the existing React
 * frontend (built to /static) and exposes the same /api/* REST surface.
 */
@SpringBootApplication
public class VinkBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(VinkBackendApplication.class, args);
    }
}
