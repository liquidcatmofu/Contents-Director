package com.juanmuscaria.modpackdirector.i18n;

import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import com.juanmuscaria.modpackdirector.util.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
