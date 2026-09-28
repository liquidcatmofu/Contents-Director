package net.jan.moddirector.universal;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniversalLocalizationClassLoaderTest {

    @Test
    void bundledMessagesLoadWhenClassLoaderHidesNonClassResources() throws Exception {
        String jarPath = System.getProperty("contentsDirector.shadowJar");
        File jar = new File(jarPath);

        try (URLClassLoader loader = new URLClassLoader(
            new URL[]{jar.toURI().toURL()},
            null
        ) {
            @Override
            public URL getResource(String name) {
                if (!name.endsWith(".class")) {
                    return null;
                }
                return super.getResource(name);
            }
        }) {
            Class<?> platformType = Class.forName(
                "com.juanmuscaria.modpackdirector.util.PlatformDelegate",
                true,
                loader
            );
            Object platform = Proxy.newProxyInstance(
                loader,
                new Class[]{platformType},
                (proxy, method, args) -> {
                    if ("headless".equals(method.getName())) {
                        return true;
                    }
                    if ("name".equals(method.getName())) {
                        return "test";
                    }
                    return null;
                }
            );

            Class<?> messagesType = Class.forName(
                "com.juanmuscaria.modpackdirector.i18n.Messages",
                true,
                loader
            );
            Object messages = messagesType
                .getConstructor(platformType, boolean.class)
                .newInstance(platform, false);

            messagesType.getMethod("setUserLocale", Locale.class)
                .invoke(messages, Locale.ENGLISH);

            Method get = messagesType.getMethod("get", String.class, Object[].class);
            String translated = (String) get.invoke(
                messages,
                "modpack_director.consent.title",
                (Object) new Object[0]
            );

            assertEquals("Review mods before installation", translated);
        }
    }
}
