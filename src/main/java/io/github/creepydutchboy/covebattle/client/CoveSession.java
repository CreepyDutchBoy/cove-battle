package io.github.creepydutchboy.covebattle.client;

import net.minecraft.client.Minecraft;

/**
 * Whether the client is in a Cove Battle session.
 *
 * <p>The signal is simply that the server has sent us a lobby snapshot — only a server running this
 * mod does that. It is what decides whether the pause menu offers "Go to Lobby" instead of
 * "Save and Quit to Title".
 */
public final class CoveSession {

    private CoveSession() {}

    public static boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && ClientLobby.received();
    }
}
