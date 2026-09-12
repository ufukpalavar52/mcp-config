package com.mcpconfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may read the configuration.
 *
 * <p>Every path, with no exception. This server hands out the database password, the JWT
 * signing key and the broker password; until this existed they went to anyone who could
 * reach port 8888, which on a laptop is every process on it and on a network is more
 * than that.
 *
 * <p>There was an exception, for {@code /actuator/health}, on the reasoning that a probe
 * has no credentials and learns nothing from the answer. Both halves were wrong. There is
 * no actuator on this classpath, so nothing served that path as a health check; what
 * served it was the config server's own {@code /&#123;application&#125;/&#123;profile&#125;}
 * route, reading it as application "actuator", profile "health" — and answering an
 * anonymous caller with the whole override property source, every credential in it.
 * A rule that names a path this server does not have cannot stay: here it did not open a
 * probe, it opened the vault.
 *
 * <p>CSRF is off and basic authentication is on because the callers are services, not
 * browsers: there is no session to fix and no form to forge, and a config client that had
 * to fetch a token first could not start.
 */
@Configuration
public class SecurityConfiguration {

    /**
     * Refuses to start without a password.
     *
     * <p>Boot treats an empty {@code spring.security.user.password} as a password, so a
     * missing value would leave the whole stack's credentials behind a login anyone can
     * pass. Failing here is loud; the alternative is a server that looks protected.
     */
    public SecurityConfiguration(@Value("${spring.security.user.password:}") String password) {
        if (password.isBlank()) {
            throw new IllegalStateException(
                    "CONFIG_PASSWORD is not set. This server hands out the database "
                            + "password, the JWT key and the broker password; it does not "
                            + "start without one. Add it to mcp-config/.env");
        }
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .httpBasic(basic -> {})
                .build();
    }
}
