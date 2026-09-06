package com.aigrama.papermoney;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for PaperStock Unified Trading Platform.
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class Application {

    /**
     * Bootstraps the Spring Boot application.
     *
     * @param args startup arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
