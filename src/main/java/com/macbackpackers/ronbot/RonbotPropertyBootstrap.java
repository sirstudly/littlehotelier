package com.macbackpackers.ronbot;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Non-web Spring Boot entry for a single property context (crh/hsh/rmb/lsh).
 * Reuses the existing Cloudbeds/DAO stack without starting HTTP or the job processor.
 * <p>
 * Uses {@link SpringBootConfiguration}+{@link EnableAutoConfiguration}+one
 * {@link ComponentScan} (not {@code @SpringBootApplication}) so Boot's default
 * package scan cannot also pick up parent-only ronbot beans.
 * <p>
 * Excludes use {@link FilterType#ASSIGNABLE_TYPE} (reliable) plus a package REGEX.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = "com.macbackpackers",
        excludeFilters = {
                @ComponentScan.Filter( type = FilterType.REGEX, pattern = "com\\.macbackpackers\\.ronbot\\..*" ),
                @ComponentScan.Filter( type = FilterType.ASSIGNABLE_TYPE, classes = {
                        RunRonbotReadApi.class,
                        PropertyContextRegistry.class,
                        RonbotReadController.class,
                        RonbotReadService.class,
                        RonbotAuthFilter.class,
                        RonbotPropertyBootstrap.class,
                        com.macbackpackers.RunProcessor.class,
                        com.macbackpackers.InsertJob.class,
                        com.macbackpackers.SecretsManagerTestApp.class
                } )
        }
)
public class RonbotPropertyBootstrap {
}
