package io.github.creepydutchboy.covebattle;

import io.github.creepydutchboy.covebattle.update.GitHubUpdater;
import io.github.creepydutchboy.covebattle.update.UpdateOutcome;
import io.github.creepydutchboy.covebattle.update.UpdateStatus;
import io.github.creepydutchboy.covebattle.update.UpdaterSettings;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Connects the Minecraft-free updater to the game: supplies the versions and the mods directory,
 * runs checks off the main thread, and remembers the latest result for the command and the
 * join notice.
 */
public final class UpdateBridge {

    public static final String JAR_NAME = "covebattle.jar";
    public static final String MANIFEST_NAME = "covebattle.update.json";

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile UpdateOutcome last = UpdateOutcome.of(UpdateStatus.UP_TO_DATE, "not checked yet");

    private UpdateBridge() {}

    public static UpdateOutcome last() {
        return last;
    }

    public static boolean isRunning() {
        return RUNNING.get();
    }

    /** Called from common setup. Honours the config and never blocks startup. */
    public static void kickOffStartupCheck() {
        if (!CoveBattleConfig.checkOnStartup) {
            last = UpdateOutcome.of(UpdateStatus.DISABLED, "startup check is disabled in the config");
            CoveBattle.LOGGER.info("Update check on startup is disabled.");
            return;
        }
        runAsync(CoveBattleConfig.autoUpdate, outcome -> {});
    }

    /**
     * Runs a check on a daemon thread.
     *
     * @param install  whether a newer release should be downloaded, or only reported
     * @param onResult called on the updater thread once finished
     */
    public static void runAsync(boolean install, Consumer<UpdateOutcome> onResult) {
        if (!RUNNING.compareAndSet(false, true)) {
            onResult.accept(UpdateOutcome.of(UpdateStatus.FAILED, "an update check is already running"));
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                UpdateOutcome outcome = run(install);
                last = outcome;
                onResult.accept(outcome);
            } catch (Throwable t) {
                UpdateOutcome outcome = UpdateOutcome.of(UpdateStatus.FAILED, t.getClass().getSimpleName() + ": " + t.getMessage());
                last = outcome;
                CoveBattle.LOGGER.warn("Update check failed unexpectedly", t);
                onResult.accept(outcome);
            } finally {
                RUNNING.set(false);
            }
        }, "covebattle-updater");
        thread.setDaemon(true);
        thread.start();
    }

    private static UpdateOutcome run(boolean install) {
        UpdaterSettings settings = settings();
        GitHubUpdater updater = new GitHubUpdater(settings, message -> CoveBattle.LOGGER.info("[update] {}", message));
        UpdateOutcome outcome = install ? updater.checkAndInstall() : updater.check();

        switch (outcome.status()) {
            case UPDATE_INSTALLED -> CoveBattle.LOGGER.info("{} {} installed. Restart to apply.",
                    CoveBattle.MOD_NAME, outcome.version());
            case UPDATE_AVAILABLE -> CoveBattle.LOGGER.info("{} {} is available (auto-install is off).",
                    CoveBattle.MOD_NAME, outcome.version());
            case FAILED -> CoveBattle.LOGGER.warn("Update check failed: {}", outcome.message());
            default -> CoveBattle.LOGGER.info("Update check: {}", outcome.message());
        }
        return outcome;
    }

    public static UpdaterSettings settings() {
        Path modsDir = FMLPaths.MODSDIR.get();
        return new UpdaterSettings(
                CoveBattleConfig.repoOwner,
                CoveBattleConfig.repoName,
                CoveBattleConfig.allowPrerelease,
                mcVersion(),
                modVersion(),
                modsDir,
                JAR_NAME,
                MANIFEST_NAME);
    }

    public static String modVersion() {
        return ModList.get().getModContainerById(CoveBattle.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("0.0.0");
    }

    public static String mcVersion() {
        return ModList.get().getModContainerById("minecraft")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
