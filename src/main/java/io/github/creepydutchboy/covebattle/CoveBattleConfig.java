package io.github.creepydutchboy.covebattle;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config file: {@code config/covebattle-common.toml}.
 *
 * <p>{@link #onLoad} is registered on the mod event bus from the mod constructor rather than
 * through {@code @EventBusSubscriber(bus = MOD)}, which NeoForge has deprecated for removal.
 */
public final class CoveBattleConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue AUTO_UPDATE;
    private static final ModConfigSpec.BooleanValue CHECK_ON_STARTUP;
    private static final ModConfigSpec.BooleanValue ALLOW_PRERELEASE;
    private static final ModConfigSpec.BooleanValue NOTIFY_IN_CHAT;
    private static final ModConfigSpec.ConfigValue<String> REPO_OWNER;
    private static final ModConfigSpec.ConfigValue<String> REPO_NAME;

    private static final ModConfigSpec.IntValue CENTRE_X;
    private static final ModConfigSpec.IntValue CENTRE_Y;
    private static final ModConfigSpec.IntValue CENTRE_Z;
    private static final ModConfigSpec.IntValue PLAY_RADIUS;
    private static final ModConfigSpec.IntValue MIN_RADIUS;
    private static final ModConfigSpec.IntValue CENTRE_TIER_RADIUS;
    private static final ModConfigSpec.IntValue PODIUM_RADIUS;

    private static final ModConfigSpec.IntValue GRACE_SECONDS;
    private static final ModConfigSpec.IntValue MATCH_SECONDS;
    private static final ModConfigSpec.IntValue BORDER_STEP_SECONDS;
    private static final ModConfigSpec.IntValue ROUND_END_SECONDS;
    private static final ModConfigSpec.IntValue RESTOCK_SECONDS;

    private static final ModConfigSpec.IntValue ROUNDS_TO_WIN;
    private static final ModConfigSpec.IntValue RESTOCK_COUNT;
    private static final ModConfigSpec.IntValue MIN_PLAYERS;
    private static final ModConfigSpec.BooleanValue FORCE_ADVENTURE;
    private static final ModConfigSpec.BooleanValue SHOWDOWN_GLOW;

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

        BUILDER.comment("The arena. Defaults match the Cove battle platform the datapack used.").push("arena");
        CENTRE_X = BUILDER.comment("Centre of the arena: the chest platform players spawn around.")
                .defineInRange("centreX", 45, -30000000, 30000000);
        CENTRE_Y = BUILDER.defineInRange("centreY", 64, -2048, 2048);
        CENTRE_Z = BUILDER.defineInRange("centreZ", 251, -30000000, 30000000);
        PLAY_RADIUS = BUILDER.comment("Radius of the play area. The world border opens at this size.")
                .defineInRange("playRadius", 80, 16, 2048);
        MIN_RADIUS = BUILDER.comment("Radius the closing border stops at.")
                .defineInRange("minRadius", 15, 4, 512);
        CENTRE_TIER_RADIUS = BUILDER.comment("Containers within this distance of the centre hold the best loot.")
                .defineInRange("centreTierRadius", 12, 1, 256);
        PODIUM_RADIUS = BUILDER.comment("How far from the centre players are placed for the grace period.")
                .defineInRange("podiumRadius", 3, 1, 64);
        BUILDER.pop();

        BUILDER.comment("Match pacing, in seconds.").push("timings");
        GRACE_SECONDS = BUILDER.comment("Frozen, invulnerable period on the podiums before the fight.")
                .defineInRange("graceSeconds", 15, 0, 300);
        MATCH_SECONDS = BUILDER.comment("Open fighting time before the border starts closing.")
                .defineInRange("matchSeconds", 90, 10, 3600);
        BORDER_STEP_SECONDS = BUILDER.comment("Seconds per border shrink step.")
                .defineInRange("borderStepSeconds", 12, 2, 120);
        ROUND_END_SECONDS = BUILDER.comment("Pause between rounds.")
                .defineInRange("roundEndSeconds", 10, 1, 120);
        RESTOCK_SECONDS = BUILDER.comment("How often looted containers are restocked.")
                .defineInRange("restockSeconds", 30, 5, 600);
        BUILDER.pop();

        BUILDER.comment("Rules.").push("rules");
        ROUNDS_TO_WIN = BUILDER.comment("Round wins needed to take the match. 2 means best of three.")
                .defineInRange("roundsToWin", 2, 1, 10);
        RESTOCK_COUNT = BUILDER.comment("Containers refilled per restock, as on console.")
                .defineInRange("restockCount", 4, 1, 64);
        MIN_PLAYERS = BUILDER.comment("Players needed to start a match.")
                .defineInRange("minPlayers", 2, 1, 16);
        FORCE_ADVENTURE = BUILDER.comment("Keep players in adventure mode. Creative and spectator are never touched,",
                        "so building and moderating still work.")
                .define("forceAdventure", true);
        SHOWDOWN_GLOW = BUILDER.comment("Make the remaining players glow once the border starts closing.")
                .define("showdownGlow", true);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean autoUpdate = true;
    public static boolean checkOnStartup = true;
    public static boolean allowPrerelease = false;
    public static boolean notifyInChat = true;
    public static String repoOwner = "CreepyDutchBoy";
    public static String repoName = "cove-battle";

    public static int centreX = 45, centreY = 64, centreZ = 251;
    public static int playRadius = 80, minRadius = 15, centreTierRadius = 12, podiumRadius = 3;
    public static int graceSeconds = 15, matchSeconds = 90, borderStepSeconds = 12;
    public static int roundEndSeconds = 10, restockSeconds = 30;
    public static int roundsToWin = 2, restockCount = 4, minPlayers = 2;
    public static boolean forceAdventure = true, showdownGlow = true;

    private CoveBattleConfig() {}

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        autoUpdate = AUTO_UPDATE.get();
        checkOnStartup = CHECK_ON_STARTUP.get();
        allowPrerelease = ALLOW_PRERELEASE.get();
        notifyInChat = NOTIFY_IN_CHAT.get();
        repoOwner = REPO_OWNER.get();
        repoName = REPO_NAME.get();

        centreX = CENTRE_X.get();
        centreY = CENTRE_Y.get();
        centreZ = CENTRE_Z.get();
        playRadius = PLAY_RADIUS.get();
        minRadius = MIN_RADIUS.get();
        centreTierRadius = CENTRE_TIER_RADIUS.get();
        podiumRadius = PODIUM_RADIUS.get();

        graceSeconds = GRACE_SECONDS.get();
        matchSeconds = MATCH_SECONDS.get();
        borderStepSeconds = BORDER_STEP_SECONDS.get();
        roundEndSeconds = ROUND_END_SECONDS.get();
        restockSeconds = RESTOCK_SECONDS.get();

        roundsToWin = ROUNDS_TO_WIN.get();
        restockCount = RESTOCK_COUNT.get();
        minPlayers = MIN_PLAYERS.get();
        forceAdventure = FORCE_ADVENTURE.get();
        showdownGlow = SHOWDOWN_GLOW.get();
    }
}
