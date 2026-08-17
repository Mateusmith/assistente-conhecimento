package br.com.contextpilot.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
