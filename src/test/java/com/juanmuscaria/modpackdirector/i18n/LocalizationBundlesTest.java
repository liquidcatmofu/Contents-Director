package com.juanmuscaria.modpackdirector.i18n;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocalizationBundlesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+\\}");

    @Test
    void japaneseContainsEveryCanonicalKeyWithMatchingPlaceholders() throws Exception {
        assertCompleteTranslation("messages_ja.xml");
    }

    @Test
    void simplifiedChineseContainsEveryCanonicalKeyWithMatchingPlaceholders() throws Exception {
        assertCompleteTranslation("messages_zh_CN.xml");
    }

    private static void assertCompleteTranslation(String localizedName) throws Exception {
        Properties canonical = load("messages.xml");
        Properties localized = load(localizedName);

        assertEquals(canonical.stringPropertyNames(), localized.stringPropertyNames());

        for (String key : canonical.stringPropertyNames()) {
            assertEquals(
                placeholders(canonical.getProperty(key)),
                placeholders(localized.getProperty(key)),
                "Placeholder mismatch for " + key + " in " + localizedName
            );
        }
    }

    private static Properties load(String name) throws Exception {
        String resource = "/com/juanmuscaria/modpackdirector/i18n/" + name;
        try (InputStream input = LocalizationBundlesTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "Missing resource " + resource);
            Properties properties = new Properties();
            properties.loadFromXML(input);
            return properties;
        }
    }

    private static Set<String> placeholders(String value) {
        Set<String> result = new HashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }
}
