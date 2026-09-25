package net.jan.moddirector.universal;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalJacksonProviderTest {

    @Test
    void shadedJarInitializesRelocatedJacksonProvider() throws Exception {
        String jarPath = System.getProperty("contentsDirector.shadowJar");
        File jar = new File(jarPath);
        assertTrue(jar.isFile(), "shadow JAR was not built");

        try (URLClassLoader loader = new URLClassLoader(
            new URL[]{jar.toURI().toURL()},
            null
        )) {
            Class<?> provider = Class.forName(
                "net.jan.moddirector.core.util.JacksonProvider",
                true,
                loader
            );
            Method getter = provider.getMethod("getObjectMapper");
            Object mapper = getter.invoke(null);

            assertEquals(
                "com.juanmuscaria.modpackdirector.shadow.jackson.databind.ObjectMapper",
                mapper.getClass().getName()
            );

            Method readTree = mapper.getClass().getMethod("readTree", String.class);
            Object node = readTree.invoke(mapper, "{\"value\":42}");
            Method get = node.getClass().getMethod("get", String.class);
            Object valueNode = get.invoke(node, "value");
            Method asInt = valueNode.getClass().getMethod("asInt");

            assertEquals(42, ((Integer) asInt.invoke(valueNode)).intValue());
        }
    }
}
