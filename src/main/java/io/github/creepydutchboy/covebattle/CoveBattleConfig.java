package io.github.creepydutchboy.covebattle;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Config file: {@code config/covebattle-common.toml}. */
@EventBusSubscriber(modid = CoveBattle.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class CoveBattleConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue AUTO_UPDATE;
    private static final ModConfigSpec.BooleanValue CHECK_ON_STARTUP;
    private static final ModConfigSpec.BooleanValue ALLOW_PRERELEASE;
    private static final ModConfigSpec.BooleanValue NOTIFY_IN_CHAT;
    private static final ModConfigSpec.ConfigValue<String> REPO_OWNER;
    private static final ModConfigSpec.ConfigValue<String> REPO_NAME;

    static {
        BUILDER.comment("Self-update settings. Updates are downloaded into the mods folder and",
                        "take effect the next time the game starts: a running JVM cannot replace",
                        "a jar it has already loaded.")
                .push("update");

        CHECK_ON_STARTUP = BUILDER
                .comment("Check GitHub for a newer release when the game starts.")
                .define("checkOnStartup", true);

        AUTO_UPDATE = BUILDER
                .comment("Install a newer release automatically. Set false to only be told one exists.")
                .define("autoUpdate", true);

        ALLOW_PRERELEASE = BUILDER
                .comment("Also accept releases marked as prerelease. Leave false for stable builds only.")
                .define("allowPrerelease", false);

        NOTIFY_IN_CHAT = BUILDER
                .comment("Tell players in chat when an update was installed or is available.")
                .define("notifyInChat", true);

        REPO_OWNER = BUILDER
                .comment("GitHub account that publishes the releases.")
                .define("repoOwner", "CreepyDutchBoy");

        REPO_NAME = BUILDER
                .comment("Repository that publishes the releases.")
                .define("repoName", "cove-battle");

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean autoUpdate = true;
    public static boolean checkOnStartup = true;
    public static boolean allowPrerelease = false;
    public static boolean notifyInChat = true;
    public static String repoOwner = "CreepyDutchBoy";
    public static String repoName = "cove-battle";

    private CoveBattleConfig() {}

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        autoUpdate = AUTO_UPDATE.get();
        checkOnStartup = CHECK_ON_STARTUP.get();
        allowPrerelease = ALLOW_PRERELEASE.get();
        notifyInChat = NOTIFY_IN_CHAT.get();
        repoOwner = REPO_OWNER.get();
        repoName = REPO_NAME.get();
    }
}
