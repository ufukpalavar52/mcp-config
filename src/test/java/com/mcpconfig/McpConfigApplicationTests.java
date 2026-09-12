package com.mcpconfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The context starts.
 *
 * <p>The properties are pinned here rather than in a {@code src/test/resources} file,
 * because a file of that name would shadow the main one entirely — and the main one is
 * where this server is told to read plain files instead of cloning a repository. Shadowed,
 * it looked for a git URI it has never had.
 */
@SpringBootTest(properties = {
        // The server refuses to start without a password, deliberately: it hands out the
        // whole stack's credentials. Obviously synthetic, so the suite does not pass by
        // borrowing the developer's own.
        "spring.security.user.name=test",
        "spring.security.user.password=test",
        // No log file under test. Empty is the off switch; an empty MCP_LOG_DIR is not,
        // because Spring reads an empty variable as a value and the path would become
        // "/mcp-config.log".
        "logging.file.name=",
})
class McpConfigApplicationTests {

    @Test
    void contextLoads() {
    }

}
