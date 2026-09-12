package com.mcpconfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What this server actually serves.
 *
 * <p>Asserted here rather than in the client because this repository owns the file. The
 * gateway used to prove its own configuration was complete by binding it and checking the
 * result; once the file moved here, that test could only ever prove the gateway's test
 * fixture matched itself. The question — does mcp-gateway receive everything it needs to
 * start — is a question about this file, so it is answered against this file.
 *
 * <p>Reads over HTTP rather than off disk, so what is asserted is the served document:
 * the merge of the service's own file with the shared one, exactly as a client sees it.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Pinned rather than read from .env: a suite that authenticated with the
        // developer's own credentials would pass on their machine and nowhere else.
        properties = {
                "spring.security.user.name=test",
                "spring.security.user.password=test",
                // No log file under test: a run should leave nothing behind on the machine
                // it ran on.
                "logging.file.name=",
        })
class ServedConfigurationTest {

    /** Keys mcp-gateway cannot start without. */
    private static final List<String> GATEWAY_REQUIRED = List.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            "spring.data.redis.host",
            "spring.data.redis.port",
            "server.port",
            "mcp.jwt.secret",
            "mcp.jwt.issuer",
            "mcp.jwt.access-token-ttl",
            "mcp.jwt.refresh-token-ttl",
            "mcp.cors.allowed-origins",
            "mcp.invitation.ttl",
            "mcp.server.url"
    );

    @LocalServerPort
    private int port;

    @Test
    void everyKeyTheGatewayNeedsIsServed() {
        Map<String, Object> served = fetch("mcp-gateway");

        assertThat(served).containsKeys(GATEWAY_REQUIRED.toArray(String[]::new));
    }

    @Test
    void theSharedFileIsMergedIn() {
        // application.yml applies to every client; a missing merge would be invisible
        // until some service quietly lost a setting it never declared itself.
        Map<String, Object> served = fetch("mcp-gateway");

        assertThat(served).containsKey("spring.threads.virtual.enabled");
    }

    @Test
    void trackedFilesCarryNoLiteralCredential() {
        /*
         * config-repo/ is committed, so a credential written into it is a credential in
         * the repository's history. Every one of them stays a ${PLACEHOLDER} there; the
         * value arrives separately, from .env, which is not tracked.
         */
        Map<String, Object> served = fetch("mcp-gateway");

        assertThat(served.get("spring.datasource.password").toString()).startsWith("${");
        assertThat(served.get("mcp.jwt.secret").toString()).startsWith("${");
        assertThat(served.get("spring.data.redis.password").toString()).startsWith("${");
    }

    @Test
    void theServerSuppliesTheVariablesItsClientsResolveAgainst() {
        /*
         * This is what lets mcp-gateway start with no credentials of its own: the
         * placeholders above resolve against properties this server sends, not against
         * the client's environment.
         *
         * Asserted by key rather than by value. The values come from .env, which is
         * absent on a fresh checkout, and a test that demanded real credentials would
         * fail for everyone who has not been given them.
         */
        Map<String, Object> served = fetch("mcp-gateway");

        assertThat(served).containsKeys(
                "DB_HOST", "DB_PORT", "DB_DATABASE", "DB_USER", "DB_PASSWORD",
                "REDIS_HOST", "REDIS_PORT", "REDIS_PASSWORD", "JWT_SECRET_KEY");
    }

    @Test
    void thoseVariablesReachEveryApplication() {
        /*
         * Pinned because it is a cost, not a feature, and one that is easy to assume away.
         * Spring Cloud Config's overrides are global: there is no per-application scope, so
         * the panel's server receives the database password too.
         *
         * It is contained rather than solved. The panel reads an allow list of panel.*
         * keys and serves only those to the browser, so nothing here reaches a visitor.
         * Anyone who can reach THIS server, though, holds the stack's credentials — which
         * is the argument for putting authentication in front of it.
         */
        Map<String, Object> panel = fetch("mcp-panel");

        assertThat(panel).containsKeys("DB_PASSWORD", "JWT_SECRET_KEY");
    }

    @Test
    void anUnknownServiceStillGetsTheSharedFile() {
        // Not an error: a new service is expected to start before it has a file here.
        Map<String, Object> served = fetch("service-that-does-not-exist");

        assertThat(served).containsKey("spring.threads.virtual.enabled");
        assertThat(served).doesNotContainKey("mcp.jwt.secret");
    }

    @Test
    void configurationIsNotServedWithoutCredentials() {
        /*
         * The point of the whole arrangement. Everything above is served on request; this
         * asserts that the request has to say who is making it, because the overrides
         * include the database password and the JWT signing key.
         */
        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri("http://localhost:{port}/mcp-gateway/default", port)
                .retrieve()
                .onStatus(status -> true, (request, ignored) -> { })
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // The refusal carries no body at all; asserted through valueOf so that stays true
        // rather than throwing, and so a future body that did leak would still be caught.
        assertThat(String.valueOf(response.getBody())).doesNotContain("datasource");
    }

    @Test
    void theHealthPathIsNotAWayInEither() {
        /*
         * This path used to be permitted, for a probe that does not exist: there is no
         * actuator on this classpath. What answered it was the /{application}/{profile}
         * route, reading "actuator" as an application and "health" as a profile, and
         * handing an anonymous caller the whole override property source.
         *
         * The test that stood here asserted the status was 200 and nothing else, so it
         * passed on the leak. This one reads the body, which is where the leak was.
         */
        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri("http://localhost:{port}/actuator/health", port)
                .retrieve()
                .onStatus(status -> true, (request, ignored) -> { })
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(String.valueOf(response.getBody()))
                .doesNotContain("DB_PASSWORD")
                .doesNotContain("JWT_SECRET_KEY")
                .doesNotContain("MCP_CIPHER_TOKEN");
    }

    /** Flattens the server's response into the property map a client would end up with. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetch(String application) {
        Map<String, Object> body = RestClient.create()
                .get()
                .uri("http://localhost:{port}/{application}/default", port, application)
                .header(HttpHeaders.AUTHORIZATION, basic("test", "test"))
                .retrieve()
                .body(Map.class);

        assertThat(body).isNotNull();
        Map<String, Object> merged = new HashMap<>();

        // Later sources are lower priority, so earlier ones must win on a clash — the
        // same order the client applies.
        List<Map<String, Object>> sources =
                (List<Map<String, Object>>) body.get("propertySources");
        for (int i = sources.size() - 1; i >= 0; i--) {
            merged.putAll((Map<String, Object>) sources.get(i).get("source"));
        }
        return merged;
    }

    private String basic(String user, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
