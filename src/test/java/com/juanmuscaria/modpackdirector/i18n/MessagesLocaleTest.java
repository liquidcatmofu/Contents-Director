package com.juanmuscaria.modpackdirector.i18n;

import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import com.juanmuscaria.modpackdirector.util.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesLocaleTest {
    private static final LoggerDelegate NOOP_LOGGER = (level, message, format) -> {
    };

    @TempDir
    Path tempDir;

    @Test
    void resolvesJapaneseBundleAndFormatsParameters() {
        Messages messages = new Messages(platform(), false);
        messages.setUserLocale(Locale.JAPANESE);

        assertEquals(
            "インストールするModを選択",
            messages.get("modpack_director.selection_page.title")
        );
        assertEquals(
            "Example をインストールしています",
            messages.get("modpack_director.progress.install", "Example")
        );
    }

    @Test
    void resolvesSimplifiedChineseBundleAndFormatsParameters() {
        Messages messages = new Messages(platform(), false);
        messages.setUserLocale(Locale.SIMPLIFIED_CHINESE);

        assertEquals(
            "选择要安装的模组",
            messages.get("modpack_director.selection_page.title")
        );
        assertEquals(
            "正在安装 Example",
            messages.get("modpack_director.progress.install", "Example")
        );
    }

    @Test
    void partialPortugueseBundleFallsBackToEnglish() {
        Messages messages = new Messages(platform(), false);
        messages.setUserLocale(new Locale("pt"));

        assertEquals(
            "Review mods before installation",
            messages.get("modpack_director.consent.title")
        );
    }

    @Test
    void externalLocaleBundleOverridesBuiltInMessagesAndKeepsFallback() throws Exception {
        Files.write(
            tempDir.resolve("messages_ja.xml"),
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n"
                + "<properties>\n"
                + "  <entry key=\"modpack_director.selection_page.title\">カスタム選択画面</entry>\n"
                + "</properties>\n").getBytes(StandardCharsets.UTF_8)
        );

        Messages messages = new Messages(platform(), true);
        messages.setUserLocale(Locale.JAPANESE);

        assertEquals(
            "カスタム選択画面",
            messages.get("modpack_director.selection_page.title")
        );
        assertEquals(
            "次へ",
            messages.get("modpack_director.selection_page.next_button_label")
        );
    }

    private PlatformDelegate platform() {
        return new PlatformDelegate() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public Path configurationDirectory() {
                return tempDir;
            }

            @Override
            public Path modFile(String modFileName) {
                return tempDir.resolve(modFileName);
            }

            @Override
            public Path rootFile(String modFileName) {
                return tempDir.resolve(modFileName);
            }

            @Override
            public Path customFile(String modFileName, String modFolderName) {
                return tempDir.resolve(modFolderName).resolve(modFileName);
            }

            @Override
            public Path installationRoot() {
                return tempDir;
            }

            @Override
            public LoggerDelegate logger() {
                return NOOP_LOGGER;
            }

            @Override
            public Side side() {
                return Side.UNKNOWN;
            }

            @Override
            public boolean headless() {
                return true;
            }
        };
    }
}
