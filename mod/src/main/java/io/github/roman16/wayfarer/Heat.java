package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Engine;
import io.github.roman16.wayfarer.story.Memories;
import io.github.roman16.wayfarer.story.Progress;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.CampfireBlock;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import toughasnails.api.temperature.IProximityBlockModifier;
import toughasnails.api.temperature.ITemperature;
import toughasnails.api.temperature.TemperatureHelper;
import toughasnails.api.temperature.TemperatureLevel;
import toughasnails.api.thirst.IThirst;
import toughasnails.api.thirst.ThirstHelper;

public final class Heat {
    private static final int FULL_THIRST = 20;
    private static final String WARMTH = "warmth";

    private Heat() {}

    public static void setup(FMLCommonSetupEvent event) {
        TemperatureHelper.registerPositionalTemperatureModifier((level, pos, temperature) -> colder(temperature, LastSea.colder(level, pos.getX() + 0.5,
                pos.getZ() + 0.5)));
        TemperatureHelper.registerPlayerTemperatureModifier((player, level) -> Memories.dreaming(player) ? TemperatureLevel.NEUTRAL
                : lastSea(player, afloat(player, fire(player, warmed(player, level)))));
        TemperatureHelper.registerProximityBlockModifier((level, pos, state) -> state.is(BlockTags.CAMPFIRES) && CampfireBlock.isLitCampfire(state)
                && Climate.of(level.getBiome(pos)) != Climate.HOT ? IProximityBlockModifier.Type.HEATING : IProximityBlockModifier.Type.NONE);
    }

    private static TemperatureLevel colder(TemperatureLevel level, int steps) {
        return steps == LastSea.FREEZING ? TemperatureLevel.ICY : TemperatureLevel.values()[Math.max(TemperatureLevel.ICY.ordinal(), level.ordinal() - steps)];
    }

    private static TemperatureLevel lastSea(Player player, TemperatureLevel level) {
        return LastSea.freezing(player) ? TemperatureLevel.ICY : level;
    }

    private static TemperatureLevel afloat(Player player, TemperatureLevel level) {
        Entity boat = player.getRootVehicle();
        boolean onWater = boat instanceof Boat && (boat.isInWater() || player.level().getFluidState(boat.blockPosition()).is(FluidTags.WATER)
                || player.level().getFluidState(boat.blockPosition().below()).is(FluidTags.WATER));
        return onWater && level.ordinal() > TemperatureLevel.NEUTRAL.ordinal() ? TemperatureLevel.values()[level.ordinal() - 1] : level;
    }

    private static TemperatureLevel fire(Player player, TemperatureLevel level) {
        return player instanceof ServerPlayer resting && Fire.resting(resting) && level.ordinal() < TemperatureLevel.NEUTRAL.ordinal() ? TemperatureLevel.NEUTRAL : level;
    }

    private static TemperatureLevel warmed(Player player, TemperatureLevel level) {
        if (!(player instanceof ServerPlayer carrier) || level.ordinal() >= TemperatureLevel.NEUTRAL.ordinal() || !Engine.running(carrier.server)
                || !Progress.of(carrier.server).gift(WARMTH)) {
            return level;
        }
        return TemperatureLevel.values()[level.ordinal() + 1];
    }

    public static CompoundTag keep(ServerPlayer player) {
        IThirst thirst = ThirstHelper.getThirst(player);
        CompoundTag body = new CompoundTag();
        body.putFloat("exhaustion", thirst.getExhaustion());
        body.putFloat("hydration", thirst.getHydration());
        body.putString("temperature", TemperatureHelper.getTemperatureData(player).getLevel().name());
        body.putInt("thirst", thirst.getThirst());
        return body;
    }

    public static void rest(ServerPlayer player) {
        temperature(player, TemperatureLevel.NEUTRAL);
        IThirst thirst = ThirstHelper.getThirst(player);
        thirst.setThirst(FULL_THIRST);
        thirst.setExhaustion(0);
    }

    public static void restore(ServerPlayer player, CompoundTag body) {
        temperature(player, TemperatureLevel.valueOf(body.getString("temperature")));
        IThirst thirst = ThirstHelper.getThirst(player);
        thirst.setExhaustion(body.getFloat("exhaustion"));
        thirst.setHydration(body.getFloat("hydration"));
        thirst.setThirst(body.getInt("thirst"));
    }

    private static void temperature(ServerPlayer player, TemperatureLevel level) {
        ITemperature temperature = TemperatureHelper.getTemperatureData(player);
        temperature.setHyperthermiaTicks(0);
        temperature.setLevel(level);
        temperature.setTargetLevel(level);
    }

    public static Optional<String> feels(ServerPlayer player) {
        return switch (TemperatureHelper.getTemperatureForPlayer(player)) {
            case ICY, COLD -> Optional.of("cold");
            case WARM, HOT -> Optional.of("hot");
            default -> Optional.empty();
        };
    }

    public static Optional<String> task(ServerPlayer player) {
        TemperatureLevel level = TemperatureHelper.getTemperatureForPlayer(player);
        TemperatureLevel coming = TemperatureHelper.getTemperatureData(player).getTargetLevel();
        return switch (level) {
            case HOT -> Optional.of("heat");
            case ICY -> Optional.of("cold");
            case WARM -> coming == TemperatureLevel.HOT ? Optional.of("heat.coming") : Optional.empty();
            case COLD -> coming == TemperatureLevel.ICY ? Optional.of("cold.coming") : Optional.empty();
            default -> Optional.empty();
        };
    }
}
