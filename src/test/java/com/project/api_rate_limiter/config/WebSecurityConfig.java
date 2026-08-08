package com.project.api_rate_limiter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local-only security chain for exercising user-based rate limiting. Lives
 * in test sources so consuming apps keep full control over their own
 * Spring Security setup. Enable via {@code rate-limiter.demo-security.enabled=true}.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnProperty(prefix = "rate-limiter.demo-security", name = "enabled", havingValue = "true")
public class WebSecurityConfig {

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain rateLimiterDemoSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()
            )
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
