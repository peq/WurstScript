package de.peeeq.wurstio.utils;

import de.peeeq.wurstscript.WLogger;
import net.moonlightflower.wc3libs.bin.GameExe;
import net.moonlightflower.wc3libs.port.GameVersion;
import net.moonlightflower.wc3libs.port.NotFoundException;
import net.moonlightflower.wc3libs.port.Orient;
import net.moonlightflower.wc3libs.port.StdGameExeFinder;
import net.moonlightflower.wc3libs.port.mac.MacGameVersionFinder;
import net.moonlightflower.wc3libs.port.win.WinGameExeFinder;

import java.io.File;
import java.io.IOException;

public class W3Utils {
    private static File gameExe;

    private static GameVersion version = null;

    /**
     * @return The wc3 patch version or -1 if none has been found
     */
    public static GameVersion getWc3PatchVersion() {
        if (gameExe == null && Orient.isWindowsSystem()) {
            try {
                gameExe = new StdGameExeFinder().get();
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        }
        if (version == null && gameExe != null) {
            try {
                version = GameExe.getVersion(gameExe);
                WLogger.info("Parsed game version: " + version);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return version;
    }

    /**
     * Pass a custom directory here to attempt parsing patch level from.
     *
     * @return The wc3 patch version or -1 if none has been found
     */
    public static GameVersion parsePatchVersion(File wc3Path) {
        try {
            if (Orient.isWindowsSystem() || Orient.isLinuxSystem()) {
                gameExe = WinGameExeFinder.fromDirIgnoreVersion(wc3Path);
                WLogger.info("Game Executable: " + gameExe);
            } else {
                WLogger.warning("Game path configuration not available");
                throw new NotFoundException();
            }
        } catch (NotFoundException e) {
            try {
                gameExe = new StdGameExeFinder().get();
            } catch (NotFoundException ex) {
                WLogger.severe(e);
            }
        }
        if (gameExe != null) {
            try {
                if (Orient.isLinuxSystem()) {
                    W3Utils.version = GameVersion.VERSION_1_32;
                } else if (Orient.isWindowsSystem()) {
                    W3Utils.version = GameExe.getVersion(gameExe);
                } else if (Orient.isMacSystem()) {
                    W3Utils.version = new MacGameVersionFinder().get();
                }
                WLogger.info("Parsed custom game version: " + version);
            } catch (IOException | NotFoundException e) {
                WLogger.severe(e);
            }
        }
        return version;
    }


    public static File getGameExe() {
        return gameExe;
    }
}
