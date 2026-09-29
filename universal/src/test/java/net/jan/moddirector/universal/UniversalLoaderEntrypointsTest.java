package net.jan.moddirector.universal;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalLoaderEntrypointsTest {
    @Test
    void preservesAllThreeLoaderEntrypointsInShadedArtifact() throws Exception {
        try (JarFile jar = new JarFile(new File(System.getProperty("contentsDirector.shadowJar")))) {
            assertEquals("com.juanmuscaria.modpackdirector.launchwrapper.ModpackDirectorTweaker",
                jar.getManifest().getMainAttributes().getValue("TweakClass"));
            assertService(jar, "cpw.mods.modlauncher.api.ITransformationService",
                "com.juanmuscaria.modpackdirector.modlauncher.ModpackDirectorService");
            assertService(jar, "net.neoforged.neoforgespi.locating.IModFileCandidateLocator",
                "com.juanmuscaria.modpackdirector.fml10.ModpackDirectorLocator");
            assertJava8ClassFiles(jar);
        }
    }

    private static void assertJava8ClassFiles(JarFile jar) throws Exception {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/versions/")) {
                continue;
            }
            try (InputStream in = jar.getInputStream(entry)) {
                byte[] header = new byte[8];
                int offset = 0;
                while (offset < header.length) {
                    int read = in.read(header, offset, header.length - offset);
                    assertTrue(read > 0, "Truncated class: " + entry.getName());
                    offset += read;
                }
                int major = (header[6] & 0xff) << 8 | (header[7] & 0xff);
                assertTrue(major <= 52, entry.getName() + " exceeds Java 8 (major " + major + ")");
            }
        }
    }

    private static void assertService(JarFile jar, String service, String provider) throws Exception {
        String classPath = provider.replace('.', '/') + ".class";
        assertNotNull(jar.getEntry(classPath), classPath);
        String descriptor = "META-INF/services/" + service;
        assertNotNull(jar.getEntry(descriptor), descriptor);
        try (InputStream in = jar.getInputStream(jar.getEntry(descriptor))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            for (int count; (count = in.read(buffer)) != -1;) {
                out.write(buffer, 0, count);
            }
            assertEquals(provider, new String(out.toByteArray(), StandardCharsets.UTF_8).trim());
        }
    }
}
