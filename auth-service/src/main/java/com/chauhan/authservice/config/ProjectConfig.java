package com.chauhan.authservice.config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RESPONSIBILITY:
 * Configures general Spring application beans. Currently defines the {@link ModelMapper} bean 
 * used to convert between database entity classes and Data Transfer Objects (DTOs).
 *
 * ISSUES / SECURITY CONCERNS:
 * 1. Unconfigured ModelMapper: A default ModelMapper can lead to deep-mapping side effects or unintended
 *    overwriting of properties if two entities have similar field structures.
 *
 * TODO:
 * - Customize ModelMapper bean configurations to skip mapping sensitive fields (like password) or enforce strict matching.
 */
@Configuration
public class ProjectConfig {
    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

}
