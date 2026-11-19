package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.story.People;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.talhanation.smallships.neoforge.events.PassengerEvents", remap = false)
abstract class SmallShipsPassengerMixin {
    @Inject(method = "onPlayerInteractWithPassenger", at = @At("HEAD"), cancellable = true, remap = false)
    private void wayfarer$storyPeopleKeepTheirSeats(PlayerInteractEvent.EntityInteract event, CallbackInfo callback) {
        if (People.story(event.getTarget())) {
            callback.cancel();
        }
    }
}
