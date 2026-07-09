package com.theragenx.icsr.config;

import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration.
 * <p>
 * All Jackson settings (SNAKE_CASE naming, NON_NULL inclusion, ISO-8601 dates,
 * fail-on-unknown-properties=false) are driven by application.properties to keep
 * this class lean. Add @Bean overrides here only if property-based config is insufficient.
 */
@Configuration
public class JacksonConfig {
    // TODO: add custom ObjectMapper beans or modules if needed beyond application.properties
}
