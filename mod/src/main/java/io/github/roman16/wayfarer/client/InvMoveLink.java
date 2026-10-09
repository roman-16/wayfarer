package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Wayfarer;
import me.pieking1215.invmove.InvMove;
import me.pieking1215.invmove.module.ModuleImpl;
import net.minecraft.client.gui.screens.Screen;

final class InvMoveLink extends ModuleImpl {
    private InvMoveLink() {}

    static void register() {
        InvMove.registerModule(new InvMoveLink());
    }

    @Override
    public String getId() {
        return Wayfarer.MODID;
    }

    @Override
    public Movement shouldAllowMovement(Screen screen) {
        return JeiLink.typing() ? Movement.FORCE_DISABLE : Movement.PASS;
    }
}
