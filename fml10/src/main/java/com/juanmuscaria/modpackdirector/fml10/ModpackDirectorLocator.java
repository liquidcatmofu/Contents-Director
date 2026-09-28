package com.juanmuscaria.modpackdirector.fml10;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import com.juanmuscaria.modpackdirector.logging.JavaLogger;
import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import com.juanmuscaria.modpackdirector.util.Side;
import net.jan.moddirector.core.manage.ModDirectorError;
import net.jan.moddirector.core.util.NetworkExceptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs before FML's built-in mods-folder locator, so newly installed mods can be found in this launch. */
public final class ModpackDirectorLocator implements IModFileCandidateLocator {
    @Override
    public int getPriority() {
        return 1001; // FML's built-in locators use priorities no higher than 1000.
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        var platform = new FmlPlatform(context.gameDirectory(), context.getRequiredDistribution());
        var director = new ModpackDirector(platform);
        platform.logger().info("Detected side: {0}", platform.side());
        try {
            if (!director.call()) {
                director.errorExit();
            }
        } catch (Exception e) {
            String detail = NetworkExceptions.isConnectivityError(e)
                    ? "Network error: " + NetworkExceptions.describe(e)
                    : "Activation error";
            director.addError(new ModDirectorError(Level.SEVERE, detail, e));
            director.errorExit();
        }
        // The built-in mods-folder locator runs after this provider and discovers installed files.
    }

    private record FmlPlatform(Path gameDir, Dist dist) implements PlatformDelegate {
        private static final LoggerDelegate LOGGER = new JavaLogger(Logger.getLogger("ModpackDirector"));

        @Override public String name() { return "NeoForgeFML10"; }

        @Override public Path configurationDirectory() {
            Path dir = gameDir.resolve("config/mod-director");
            try {
                return Files.createDirectories(dir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override public Path modFile(String name) { return gameDir.resolve("mods").resolve(name); }
        @Override public Path rootFile(String name) { return gameDir.resolve(name); }
        @Override public Path customFile(String name, String folder) { return gameDir.resolve(folder).resolve(name); }
        @Override public Path installationRoot() { return gameDir; }
        @Override public LoggerDelegate logger() { return LOGGER; }
        @Override public Side side() { return dist == Dist.CLIENT ? Side.CLIENT : Side.SERVER; }
        @Override public boolean headless() { return GraphicsEnvironment.isHeadless(); }
    }
}
