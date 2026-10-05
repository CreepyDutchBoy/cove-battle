package io.github.creepydutchboy.covebattle.client;

import io.github.creepydutchboy.covebattle.rules.MatchMode;
import io.github.creepydutchboy.covebattle.rules.Mutators;

/** The client's copy of the server's rule set, kept current by {@code SyncRulesPayload}. */
public final class ClientRules {

    private static MatchMode mode = MatchMode.REMASTERED;
    private static Mutators mutators = Mutators.remastered();
    private static boolean received;

    private ClientRules() {}

    public static void accept(MatchMode newMode, Mutators newMutators) {
        mode = newMode;
        mutators = newMutators;
        received = true;
    }

    public static MatchMode mode() {
        return mode;
    }

    public static Mutators mutators() {
        return mutators;
    }

    /** False until the server has spoken, so the screen can say so rather than show made-up numbers. */
    public static boolean received() {
        return received;
    }

    public static void reset() {
        received = false;
        mode = MatchMode.REMASTERED;
        mutators = Mutators.remastered();
    }
}
