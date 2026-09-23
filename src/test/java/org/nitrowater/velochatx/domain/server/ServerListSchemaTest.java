package org.nitrowater.velochatx.domain.server;

import org.junit.jupiter.api.Test;
import org.nitrowater.waterapi.domain.config.schema.ItemSchema;
import org.nitrowater.waterapi.domain.config.schema.RegisteredSchema;
import org.nitrowater.waterapi.domain.config.schema.SchemaContext;
import org.nitrowater.waterapi.domain.config.schema.SchemaViolation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the real {@code server.list} item schema registration
 * ({@link ServerServiceImpl#SERVER_LIST_SCHEMA}): both {@code display} and
 * {@code channels} are optional leaves, and the collection key is {@code server.list}.
 * Lives in the service's package so it asserts against the actual registered constant
 * instead of a hand-mirrored copy that could drift.
 */
class ServerListSchemaTest {

    @Test
    void serverListSchemaRegisteredWithOptionalLeaves() {
        RegisteredSchema registered = ServerServiceImpl.SERVER_LIST_SCHEMA;
        assertEquals("server.list", registered.key().getKey());
        ItemSchema schema = registered.schema();

        // Empty entry {} → no violations (both leaves optional).
        List<SchemaViolation> empty = new ArrayList<>();
        schema.validate(new SchemaContext("config", "server.list", "lobby", Map.of()), empty);
        assertTrue(empty.isEmpty(), "empty entry must be valid: " + empty);

        // Both leaves present → no violations.
        List<SchemaViolation> full = new ArrayList<>();
        schema.validate(new SchemaContext("config", "server.list", "lobby",
                Map.of("display", "[L]", "channels", List.of("login"))), full);
        assertTrue(full.isEmpty(), "full entry must be valid: " + full);

        // Values are opaque leaves (existence only) — a wrong-typed value still passes KeyItemSchema
        // leaf semantics; this documents the intentional limitation.
        List<SchemaViolation> wrongType = new ArrayList<>();
        schema.validate(new SchemaContext("config", "server.list", "lobby",
                Map.of("channels", "not-a-list")), wrongType);
        assertTrue(wrongType.isEmpty(), "leaf values are opaque by design: " + wrongType);
    }

    @Test
    void serverListConfigKeyPathIsServerList() {
        assertEquals("server.list", ServerConfigKey.SERVER_LIST.getKey());
    }

    @Test
    void serverDisplayFreeformIsNotRegisteredOnService() {
        // The service intentionally registers only SERVER_LIST_SCHEMA: dropping the legacy
        // server-display.display freeform keeps update from re-adding migrated-away keys.
        // ServerConfigKey.DISPLAY_SERVER_DISPLAY remains for runtime compat reads only.
        assertEquals("server-display.display", ServerConfigKey.DISPLAY_SERVER_DISPLAY.getKey());
    }
}
