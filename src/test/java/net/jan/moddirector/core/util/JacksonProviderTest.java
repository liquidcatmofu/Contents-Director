package net.jan.moddirector.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JacksonProviderTest {

    @Test
    void parsesJsonWithConfiguredObjectMapper() throws Exception {
        ObjectMapper mapper = JacksonProvider.getObjectMapper();
        JsonNode node = mapper.readTree("{\"name\":\"contents-director\",\"enabled\":true}");

        assertNotNull(node);
        assertEquals("contents-director", node.get("name").asText());
        assertEquals(true, node.get("enabled").asBoolean());
    }
}
