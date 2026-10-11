package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import net.combat_roll.client.CombatRollClient;
import net.combat_roll.client.Keybindings;
import net.combat_roll.client.gui.HudElement;
import net.combat_roll.internals.RollManager;
import net.combat_roll.internals.RollingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;

public final class RollHud {
    private static final ResourceLocation ROLL = Wayfarer.id("stamp/roll");
    private static final int ROLL_WIDTH = 6;
    private static final int ROLL_HEIGHT = 9;
    private static final int ROLL_GAP = 2;
    private static final int PLATE_HEIGHT = 13;
    private static final int PLATE_PAD = 4;
    private static final int CAP_GAP = 3;
    private static final int LIT = 3;
    private static final int UNLIT = 0;

    private RollHud() {}

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSpectator() || player.isCreative() && !CombatRollClient.config.showHUDInCreative) {
            return;
        }
        RollManager.CooldownInfo cooldown = ((RollingEntity) player).getRollManager().getCooldown();
        if (!CombatRollClient.config.showWhenFull && cooldown.availableRolls() == cooldown.maxRolls()) {
            return;
        }
        HudElement widget = CombatRollClient.hudConfig.value.rollWidget;
        Vec2 origin = widget.origin.getPoint(graphics.guiWidth(), graphics.guiHeight());
        int x = (int) (origin.x + widget.offset.x);
        int y = (int) (origin.y + widget.offset.y) - PLATE_HEIGHT / 2;
        int width = PLATE_PAD * 2 + cooldown.maxRolls() * (ROLL_WIDTH + ROLL_GAP) - ROLL_GAP;
        if (CombatRollClient.config.showKeybinding) {
            Font font = minecraft.font;
            String key = Keybindings.roll.getTranslatedKeyMessage().getString();
            Stamp.keycap(graphics, font, key, x - Stamp.keycapWidth(font, key) - CAP_GAP, y + (PLATE_HEIGHT - Stamp.KEYCAP_HEIGHT) / 2 + 2);
        }
        graphics.blitSprite(Stamp.PLATE, x, y, width, PLATE_HEIGHT);
        int top = y + (PLATE_HEIGHT - ROLL_HEIGHT) / 2;
        for (int index = 0; index < cooldown.maxRolls(); index++) {
            int left = x + PLATE_PAD + index * (ROLL_WIDTH + ROLL_GAP);
            Stamp.tinted(graphics, ROLL, left, top, ROLL_WIDTH, ROLL_HEIGHT, Stamp.ramp("ember", UNLIT));
            float full = index < cooldown.availableRolls() ? 1 : index == cooldown.availableRolls() ? Math.min(1f, (float) cooldown.elapsed() / cooldown.total()) : 0;
            int lit = Math.round(full * ROLL_HEIGHT);
            if (lit > 0) {
                graphics.enableScissor(left, top + ROLL_HEIGHT - lit, left + ROLL_WIDTH, top + ROLL_HEIGHT);
                Stamp.tinted(graphics, ROLL, left, top, ROLL_WIDTH, ROLL_HEIGHT, Stamp.ramp("ember", LIT));
                graphics.disableScissor();
            }
        }
    }
}
