package io.github.creepydutchboy.covebattle.update;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Standalone entry point for exercising the updater without Minecraft.
 *
 * <pre>
 * java -cp build/classes/java/main:gson.jar \
 *   io.github.creepydutchboy.covebattle.update.UpdaterCli \
 *   --current 1.0.0 --mc 1.21.1 --mods /tmp/testmods [--apply] [--prerelease]
 * </pre>
 */
public final class UpdaterCli {

    public static void main(String[] args) {
        Map<String, String> opts = new HashMap<>();
        boolean apply = false;
        boolean prerelease = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--apply" -> apply = true;
                case "--prerelease" -> prerelease = true;
                default -> {
                    if (args[i].startsWith("--") && i + 1 < args.length) opts.put(args[i].substring(2), args[++i]);
                }
            }
        }

        UpdaterSettings settings = new UpdaterSettings(
                opts.getOrDefault("owner", "CreepyDutchBoy"),
                opts.getOrDefault("repo", "cove-battle"),
                prerelease,
                opts.getOrDefault("mc", "1.21.1"),
                opts.getOrDefault("current", "0.0.0"),
                Path.of(opts.getOrDefault("mods", "/tmp/covebattle-test-mods")),
                opts.getOrDefault("jar", "covebattle.jar"),
                opts.getOrDefault("manifest", "covebattle.update.json"));

        System.out.println("repo      : " + settings.owner() + "/" + settings.repo());
        System.out.println("current   : " + settings.currentVersion());
        System.out.println("minecraft : " + settings.mcVersion());
        System.out.println("mods dir  : " + settings.modsDir());
        System.out.println("channel   : " + (prerelease ? "stable + prerelease" : "stable only"));
        System.out.println("mode      : " + (apply ? "check and install" : "check only"));
        System.out.println();

        GitHubUpdater updater = new GitHubUpdater(settings, msg -> System.out.println("  [updater] " + msg));
        UpdateOutcome outcome = apply ? updater.checkAndInstall() : updater.check();

        System.out.println();
        System.out.println("result    : " + outcome.status());
        System.out.println("message   : " + outcome.message());
        System.out.println("version   : " + (outcome.version() == null ? "-" : outcome.version()));
        System.exit(outcome.status() == UpdateStatus.FAILED ? 1 : 0);
    }
}
