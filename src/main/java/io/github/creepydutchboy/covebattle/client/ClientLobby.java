package io.github.creepydutchboy.covebattle.client;

import io.github.creepydutchboy.covebattle.lobby.LobbySnapshot;

import java.util.List;

/** The client's copy of the lobby, refreshed by {@code SyncLobbyPayload}. */
public final class ClientLobby {

    private static LobbySnapshot snapshot =
            new LobbySnapshot("LOBBY", "Remastered", "", 0, 3, false, 0, List.of());
    private static boolean received;

    private ClientLobby() {}

    public static void accept(LobbySnapshot incoming) {
        snapshot = incoming;
        received = true;
    }

    public static LobbySnapshot snapshot() {
        return snapshot;
    }

    public static boolean received() {
        return received;
    }

    public static void reset() {
        received = false;
        snapshot = new LobbySnapshot("LOBBY", "Remastered", "", 0, 3, false, 0, List.of());
    }
}
