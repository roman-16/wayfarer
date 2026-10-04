package io.github.roman16.wayfarer.spike;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.markusbordihn.easynpc.data.objective.ObjectiveDataEntry;
import de.markusbordihn.easynpc.data.objective.ObjectiveType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class ActsSpike {
    private static final String BODY = "easy_npc:humanoid";
    private static final int GAMEMASTER = 2;
    private static final int LISTENING = 48;

    private ActsSpike() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("acts")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("noise").then(Commands.argument("at", BlockPosArgument.blockPos())
                                .executes(context -> noise(context.getSource(), BlockPosArgument.getBlockPos(context, "at")))))
                        .then(Commands.literal("listen").executes(context -> listen(context.getSource())))
                        .then(Commands.literal("person").then(Commands.argument("objective", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> person(context.getSource(), StringArgumentType.getString(context, "objective"),
                                                EntityArgument.getPlayer(context, "player"))))))));
    }

    private static int noise(CommandSourceStack source, BlockPos at) {
        ServerLevel level = source.getLevel();
        level.playSound(null, at, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 2.0f, 1.0f);
        say(source.getServer(), "acts: a clang at %d %d %d".formatted(at.getX(), at.getY(), at.getZ()));
        return 1;
    }

    private static int listen(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        List<AbstractIllager> gazebound = level.getEntitiesOfClass(AbstractIllager.class, new AABB(BlockPos.containing(source.getPosition())).inflate(LISTENING));
        for (AbstractIllager illager : gazebound) {
            say(source.getServer(), "acts: %s at %d %d %d, %s, %s".formatted(illager.getType().getDescription().getString(), illager.getBlockX(),
                    illager.getBlockY(), illager.getBlockZ(), illager.getNavigation().isDone() ? "standing" : "walking to " + illager.getNavigation().getTargetPos(),
                    illager.getTarget() == null ? "hunting nobody" : "hunting " + illager.getTarget().getName().getString()));
        }
        if (gazebound.isEmpty()) {
            say(source.getServer(), "acts: no Gazebound within %d blocks".formatted(LISTENING));
        }
        return 1;
    }

    private static int person(CommandSourceStack source, String objective, ServerPlayer player) throws CommandSyntaxException {
        ServerPlayer here = source.getPlayerOrException();
        ServerLevel level = here.serverLevel();
        Entity body = EntityType.byString(BODY).map(type -> type.create(level)).orElse(null);
        if (!(body instanceof EasyNPC<?> npc)) {
            say(source.getServer(), "acts failed: Easy NPC is not in this game");
            return 0;
        }
        body.moveTo(here.getX(), here.getY(), here.getZ(), 0, 0);
        body.setCustomName(Component.literal(objective.equals("follow") ? "A follower" : "A fleer"));
        if (body instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        level.addFreshEntity(body);
        ObjectiveDataEntry entry = new ObjectiveDataEntry(objective.equals("follow") ? ObjectiveType.FOLLOW_PLAYER : ObjectiveType.FLEE_PLAYER);
        entry.setTargetPlayerName(player.getScoreboardName());
        npc.getEasyNPCObjectiveData().addOrUpdateCustomObjective(entry);
        say(source.getServer(), "acts: a person who %ss %s stands at %d %d %d".formatted(objective, player.getScoreboardName(), body.getBlockX(),
                body.getBlockY(), body.getBlockZ()));
        return 1;
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}
