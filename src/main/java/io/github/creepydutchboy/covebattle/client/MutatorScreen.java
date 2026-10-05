package io.github.creepydutchboy.covebattle.client;

import io.github.creepydutchboy.covebattle.net.SetRulesPayload;
import io.github.creepydutchboy.covebattle.rules.MatchMode;
import io.github.creepydutchboy.covebattle.rules.MutatorSpec;
import io.github.creepydutchboy.covebattle.rules.Mutators;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The mutator board: a slider for every tunable, and the three modes along the top.
 *
 * <p>Edits are local until Apply, which sends one payload; the server validates and broadcasts the
 * result, so everyone's board agrees. Picking Classic or Remastered loads that preset into the
 * sliders, which makes them a starting point for a custom set rather than a dead end.
 */
public class MutatorScreen extends Screen {

    private static final int COLUMNS = 2;
    private static final int SLIDER_H = 18;
    private static final int ROW_GAP = 2;

    private final Screen parent;
    private final List<MutatorSlider> sliders = new ArrayList<>();
    private MatchMode selected = MatchMode.REMASTERED;
    private Mutators working = Mutators.remastered();
    private Component status = Component.empty();
    private int page;
    private int pages = 1;
    private int sliderWidth = 196;
    /** init() runs again on every rebuild, so the server copy is only pulled in once. */
    private boolean loaded;

    public MutatorScreen(Screen parent) {
        super(Component.literal("Cove Battle — Modes & Mutators"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!loaded) {
            selected = ClientRules.mode();
            working = ClientRules.mutators();
            loaded = true;
        }
        sliders.clear();

        int centreX = this.width / 2;
        int modeY = 40;
        int modeW = Math.min(110, (this.width - 40) / 3 - 4);
        int modeX = centreX - (modeW * 3 + 8) / 2;
        for (MatchMode mode : MatchMode.values()) {
            int x = modeX + mode.ordinal() * (modeW + 4);
            addRenderableWidget(Button.builder(
                            Component.literal((mode == selected ? "\u25B8 " : "") + mode.label()),
                            button -> chooseMode(mode))
                    .bounds(x, modeY, modeW, 20)
                    .build());
        }

        // Lay the board out for the space actually available: Minecraft's GUI scale can leave a
        // logical screen barely 240 tall, which is not enough for twenty rows, so it paginates.
        int gridTop = modeY + 26;
        int footerHeight = 46;
        int available = Math.max(SLIDER_H + ROW_GAP, this.height - gridTop - footerHeight);
        int rowsPerColumn = Math.max(1, available / (SLIDER_H + ROW_GAP));
        int perPage = Math.max(1, rowsPerColumn * COLUMNS);
        pages = Math.max(1, (MutatorSpec.ALL.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));

        sliderWidth = Math.min(196, (this.width - 36) / COLUMNS - 8);
        int gridLeft = centreX - (COLUMNS * sliderWidth + (COLUMNS - 1) * 8) / 2;

        int first = page * perPage;
        int last = Math.min(MutatorSpec.ALL.size(), first + perPage);
        for (int i = first; i < last; i++) {
            MutatorSpec spec = MutatorSpec.ALL.get(i);
            int indexOnPage = i - first;
            int column = indexOnPage / rowsPerColumn;
            int row = indexOnPage % rowsPerColumn;
            int x = gridLeft + column * (sliderWidth + 8);
            int y = gridTop + row * (SLIDER_H + ROW_GAP);
            MutatorSlider slider = new MutatorSlider(x, y, sliderWidth, spec, working.value(spec.key()));
            sliders.add(slider);
            addRenderableWidget(slider);
        }

        int footerY = this.height - 28;
        int buttonW = Math.min(80, (this.width - 40) / 5);
        int x = centreX - (buttonW * 5 + 16) / 2;
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> apply())
                .bounds(x, footerY, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reload"), button -> reload())
                .bounds(x + buttonW + 4, footerY, buttonW, 20).build());
        Button prev = Button.builder(Component.literal("< Page"), button -> turnPage(-1))
                .bounds(x + (buttonW + 4) * 2, footerY, buttonW, 20).build();
        Button next = Button.builder(Component.literal("Page >"), button -> turnPage(1))
                .bounds(x + (buttonW + 4) * 3, footerY, buttonW, 20).build();
        prev.active = pages > 1;
        next.active = pages > 1;
        addRenderableWidget(prev);
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(x + (buttonW + 4) * 4, footerY, buttonW, 20).build());
    }

    /** Keeps slider edits when changing page, so a long board can be filled in over several pages. */
    private void turnPage(int delta) {
        captureSliders();
        page = Math.floorMod(page + delta, pages);
        rebuild();
    }

    private void captureSliders() {
        Mutators edited = working;
        for (MutatorSlider slider : sliders) {
            edited = edited.with(slider.spec.key(), slider.currentValue());
        }
        working = edited.sanitised();
    }

    private void chooseMode(MatchMode mode) {
        captureSliders();
        selected = mode;
        if (mode != MatchMode.MUTATORS) {
            // Loading the preset into the sliders shows exactly what that mode does.
            working = Mutators.forMode(mode);
        }
        rebuild();
    }

    private void reload() {
        loaded = false;
        selected = ClientRules.mode();
        working = ClientRules.mutators();
        status = Component.literal("Reloaded from the server").withStyle(ChatFormatting.GRAY);
        loaded = true;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        sliders.clear();
        init();
    }

    private void apply() {
        captureSliders();

        if (this.minecraft == null || this.minecraft.getConnection() == null) {
            status = Component.literal("Join a world first — there is no server to send this to.")
                    .withStyle(ChatFormatting.RED);
            return;
        }
        try {
            PacketDistributor.sendToServer(new SetRulesPayload(selected, working));
            status = Component.literal("Sent to the server").withStyle(ChatFormatting.GREEN);
        } catch (Exception e) {
            status = Component.literal("Could not send: " + e.getMessage()).withStyle(ChatFormatting.RED);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centreX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centreX, 14, 0xFFD24A);
        graphics.drawCenteredString(this.font,
                Component.literal(selected.blurb()).withStyle(ChatFormatting.GRAY), centreX, 28, 0xAAAAAA);

        Component footer = !ClientRules.received()
                ? Component.literal("Showing defaults — the server has not sent its rules yet")
                        .withStyle(ChatFormatting.DARK_GRAY)
                : this.status;
        if (pages > 1) {
            graphics.drawCenteredString(this.font,
                    Component.literal("Page " + (page + 1) + " of " + pages).withStyle(ChatFormatting.DARK_GRAY),
                    centreX, this.height - 40, 0x888888);
        }
        if (!footer.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, footer, centreX, this.height - 40 + (pages > 1 ? 0 : 0) - 10, 0xFFFFFF);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    /** One tunable. Booleans use the same widget with a two-stop range, so the board reads uniformly. */
    private static class MutatorSlider extends AbstractSliderButton {
        private final MutatorSpec spec;

        MutatorSlider(int x, int y, int width, MutatorSpec spec, float initial) {
            super(x, y, width, SLIDER_H, Component.empty(), spec.toSlider(initial));
            this.spec = spec;
            updateMessage();
        }

        float currentValue() {
            return spec.fromSlider(this.value);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(spec.label() + ": ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(spec.format(currentValue())).withStyle(ChatFormatting.WHITE)));
        }

        @Override
        protected void applyValue() {
            // Values are collected on Apply; nothing to push per drag.
        }
    }
}
