package com.mcpconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Configuration server for the MCP stack.
 *
 * <p>Serves what every other service used to carry in its own {@code application.yml}, so
 * a setting changes in one place rather than once per repository. The files it serves live
 * under {@code config-repo/}.
 *
 * <p>It holds structure, not secrets. Passwords and keys stay as {@code ${PLACEHOLDER}}
 * and are resolved by the client from its own environment, which means this server can be
 * read by anyone who can reach it without that being a disclosure.
 */
@EnableConfigServer
@SpringBootApplication
public class McpConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpConfigApplication.class, args);
    }

}
