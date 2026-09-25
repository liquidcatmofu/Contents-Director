package com.juanmuscaria.modpackdirector.ui;

import com.juanmuscaria.modpackdirector.i18n.Messages;
import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import com.juanmuscaria.modpackdirector.util.Side;
import net.jan.moddirector.core.manage.ModDirectorError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalUiClientLocalizationTest {
    private static final LoggerDelegate NOOP_LOGGER = (level, message, format) -> {
    };

    @TempDir
    Path tempDir;

    @Test
    void manualDownloadRequestCarriesJapaneseUiTextToHelper() throws Exception {
        Messages messages = messages(Locale.JAPANESE);

        ExternalUiProtocol.Request request = ExternalUiClient.createManualDownloadRequest(
            new URL("https://example.invalid/file.jar"),
            tempDir.resolve("mods").resolve("file.jar"),
            "file.jar",
            messages
        );

        assertEquals("手動ダウンロードが必要です", request.title);
        assertEquals(
            "ブラウザーで開く",
            request.localizedText.get("modpack_director.manual_download.open_browser")
        );
        assertEquals(
            "ダウンロードしたファイルを選択",
            request.localizedText.get("modpack_director.manual_download.chooser_title")
        );
    }

    @Test
    void errorRequestCarriesSimplifiedChineseUiTextToHelper() {
        Messages messages = messages(Locale.SIMPLIFIED_CHINESE);

        ExternalUiProtocol.Request request = ExternalUiClient.createErrorRequest(
            Collections.singletonList(new ModDirectorError(Level.SEVERE, "download failed")),
            messages
        );

        assertEquals("安装失败", request.title);
        assertEquals("关闭", request.buttonLabel);
        assertEquals("错误", request.errors.get(0).level);
        assertEquals(
            "原因：",
            request.localizedText.get("modpack_director.error.cause")
        );
    }

    private Messages messages(Locale locale) {
        Messages messages = new Messages(platform(), false);
        messages.setUserLocale(locale);
        return messages;
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
