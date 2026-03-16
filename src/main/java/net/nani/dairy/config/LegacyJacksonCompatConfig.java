package net.nani.dairy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LegacyJacksonCompatConfig {

    @Bean
    public ObjectMapper legacyObjectMapper() {
        return new ObjectMapper();
    }
}
