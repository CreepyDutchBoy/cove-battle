package io.github.creepydutchboy.covebattle.client;

import io.github.creepydutchboy.covebattle.client.ui.CoveButton;
import io.github.creepydutchboy.covebattle.client.ui.CoveScreen;
import io.github.creepydutchboy.covebattle.client.ui.CoveTheme;
import io.github.creepydutchboy.covebattle.client.ui.CoveUI;
import io.github.creepydutchboy.covebattle.lobby.LobbySnapshot;
import io.github.creepydutchboy.covebattle.net.LobbyActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The lobby. It is a screen rather than a room: the world is loaded behind it so the server can
 * run, but nobody walks around a waiting area. Players can sit here while a match carries on
 * without them, and drop back in when they want.
 */
public class CoveLobbyScreen extends CoveScreen {

    /** Set just before a world loads so the lobby opens instead of the world. */
    private static boolean openOnLoad;

    private EditBox portBox;
    private String status = "";

    public CoveLobbyScreen(net.minecraft.client.gui.screens.Screen parent) {
        super(parent, "Cove Battle");
        this.footerNote = "Esc closes the lobby and drops you into the world";
    }

    public static void openOnLoad() {
        openOnLoad = true;
    }

    public static boolean consumeOpenOnLoad() {
        boolean value = openOnLoad;
        openOnLoad = false;
        return value;
    }

    @Override
    protected int contentWidth() {
        return Math.min(this.width - 16, 360);
    }

    @Override
    protected int contentHeight() {
        return Math.min(this.height - 16, 210);
    }

    @Override
    protected String headerNote() {
        LobbySnapshot s = ClientLobby.snapshot();
        return s.hosting() ? "OPEN ON PORT " + s.port() : "LOCAL";
    }

    private int listWidth() {
        return Math.max(110, (contentWidth() - 10) * 45 / 100);
    }

    @Override
    protected void init() {
        LobbySnapshot snapshot = ClientLobby.snapshot();
        int x = frameX();
        int y = frameY() + CoveTheme.HEADER;
        int w = contentWidth();
        int right = x + listWidth() + 10;
        int rightW = w - listWidth() - 10;

        boolean inMatch = !"LOBBY".equals(snapshot.phase());
        boolean meInLobby = isSelfInLobby(snapshot);

        int row = y + 56;
        addRenderableWidget(new CoveButton(right, row, rightW, 18,
                Component.literal(inMatch ? "Start another match" : "Start match — Solo"),
                () -> send(LobbyActionPayload.Action.START_SOLO, ""))
                .accent(CoveTheme.GREEN));
        addRenderableWidget(new CoveButton(right, row + 20, rightW, 18,
                Component.literal("Start match — Teams"),
                () -> send(LobbyActionPayload.Action.START_TEAMS, ""))
                .accent(CoveTheme.GREEN));

        addRenderableWidget(new CoveButton(right, row + 42, rightW, 18,
                Component.literal(meInLobby ? "Join the game" : "Go to lobby"),
                () -> {
                    send(meInLobby ? LobbyActionPayload.Action.LEAVE_LOBBY : LobbyActionPayload.Action.ENTER_LOBBY, "");
                    if (meInLobby && this.minecraft != null) this.minecraft.setScreen(null);
                })
                .accent(CoveTheme.ACCENT));

        addRenderableWidget(new CoveButton(right, row + 62, rightW, 18,
                Component.literal("Modes & Mutators"),
                () -> this.minecraft.setScreen(new MutatorScreen(this)))
                .accent(CoveTheme.BLUE));

        // Hosting controls
        // Sits below the last button rather than being pinned to the frame, so it cannot
        // land on top of the footer at small sizes.
        int hostY = row + 86;
        portBox = new EditBox(this.font, right, hostY, 56, 16, Component.literal("port"));
        portBox.setValue(snapshot.port() > 0 ? String.valueOf(snapshot.port()) : "25565");
        portBox.setMaxLength(5);
        addRenderableWidget(portBox);

        CoveButton open = new CoveButton(right + 60, hostY, rightW - 60, 16,
                Component.literal(snapshot.hosting() ? "Open" : "Open to players"),
                () -> send(LobbyActionPayload.Action.OPEN_TO_LAN, portBox.getValue()))
                .accent(CoveTheme.TEAL);
        open.active = !snapshot.hosting();
        addRenderableWidget(open);
    }

    private boolean isSelfInLobby(LobbySnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return true;
        String me = minecraft.player.getGameProfile().getName();
        for (LobbySnapshot.Entry entry : snapshot.entries()) {
            if (entry.name().equals(me)) return entry.where() == LobbySnapshot.Where.LOBBY;
        }
        return true;
    }

    private void send(LobbyActionPayload.Action action, String argument) {
        try {
            PacketDistributor.sendToServer(new LobbyActionPayload(action, argument));
            status = "";
        } catch (Exception e) {
            status = "Not connected";
        }
        rebuildSoon = true;
    }

    private boolean rebuildSoon;
    private int lastEntryCount = -1;
    private String lastPhase = "";

    @Override
    public void tick() {
        LobbySnapshot snapshot = ClientLobby.snapshot();
        if (rebuildSoon || snapshot.entries().size() != lastEntryCount || !snapshot.phase().equals(lastPhase)) {
            rebuildSoon = false;
            lastEntryCount = snapshot.entries().size();
            lastPhase = snapshot.phase();
            clearWidgets();
            init();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int x, int y, int width, int height,
                                 int mouseX, int mouseY, float partialTick) {
        LobbySnapshot snapshot = ClientLobby.snapshot();
        int listW = listWidth();
        int right = x + listW + 10;
        int rightW = width - listW - 10;

        // Players
        CoveUI.panel(graphics, x, y, listW, height - 4, CoveTheme.PANEL);
        graphics.drawString(this.font, Component.literal("PLAYERS"), x + 6, y + 5, CoveTheme.TEXT_DIM, false);
        int rowY = y + 18;
        if (snapshot.entries().isEmpty()) {
            graphics.drawString(this.font, Component.literal("nobody here yet"), x + 6, rowY, CoveTheme.TEXT_FAINT, false);
        }
        for (LobbySnapshot.Entry entry : snapshot.entries()) {
            if (rowY > y + height - 18) break;
            int colour = switch (entry.where()) {
                case PLAYING -> CoveTheme.GREEN;
                case OUT -> CoveTheme.TEXT_FAINT;
                case LOBBY -> CoveTheme.TEXT;
            };
            graphics.drawString(this.font, Component.literal(entry.name()), x + 6, rowY, colour, false);
            String tag = switch (entry.where()) {
                case PLAYING -> entry.side().isEmpty() ? "IN MATCH" : entry.side().toUpperCase();
                case OUT -> "OUT";
                case LOBBY -> "LOBBY";
            };
            int tagColour = "RED".equals(tag) ? CoveTheme.RED : "BLUE".equals(tag) ? CoveTheme.BLUE : colour;
            int tagW = this.font.width(tag) + 8;
            CoveUI.chip(graphics, this.font, x + listW - tagW - 6, rowY, tag, tagColour);
            rowY += 13;
        }

        // Match state
        CoveUI.panel(graphics, right, y, rightW, 50, CoveTheme.PANEL);
        graphics.drawString(this.font, Component.literal("MATCH"), right + 6, y + 5, CoveTheme.TEXT_DIM, false);
        CoveUI.statRow(graphics, this.font, right + 6, y + 18, rightW - 12, "Rules", snapshot.modeLabel(), CoveTheme.ACCENT);
        CoveUI.statRow(graphics, this.font, right + 6, y + 29, rightW - 12, "State",
                "LOBBY".equals(snapshot.phase()) ? "waiting" : snapshot.phase().toLowerCase(),
                "LOBBY".equals(snapshot.phase()) ? CoveTheme.TEXT_DIM : CoveTheme.GREEN);
        CoveUI.statRow(graphics, this.font, right + 6, y + 40, rightW - 12, "Round",
                snapshot.round() == 0 ? "-" : snapshot.round() + " / best of " + snapshot.bestOf(), CoveTheme.TEXT);

        if (!status.isEmpty()) {
            graphics.drawString(this.font, Component.literal(status), right, y + height - 30, CoveTheme.RED, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }
}
