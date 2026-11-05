package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Engine;
import io.github.roman16.wayfarer.story.Memories;
import io.github.roman16.wayfarer.story.Pages;
import io.github.roman16.wayfarer.story.Talk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Halls {
    public static final TagKey<Structure> ALL = TagKey.create(Registries.STRUCTURE, Wayfarer.id("halls"));

    private static final int TITLE_CHECK_TICKS = 20;
    private static final String SHARD_TAKEN = "shard_taken";
    private static final Map<UUID, ResourceLocation> INSIDE = new HashMap<>();

    private Halls() {}

    public static Optional<ResourceLocation> inside(ServerLevel level, BlockPos pos) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, ALL);
        if (!start.isValid() || !(start.getStructure() instanceof HallStructure hall)) {
            return Optional.empty();
        }
        boolean within = start.getPieces().stream().anyMatch(piece -> piece instanceof HallPiece part
                && part.is(hall.pieces().hall())
                && piece.getBoundingBox().isInside(pos));
        return within ? Optional.ofNullable(level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(hall)) : Optional.empty();
    }

    public static boolean built(ServerLevel level, BlockPos pos) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, ALL);
        return start.isValid() && start.getStructure() instanceof HallStructure;
    }

    public static boolean inside(ServerPlayer player) {
        return inside(player.serverLevel(), player.blockPosition()).isPresent();
    }

    public static List<Holder<Structure>> members(ServerLevel level, TagKey<Structure> tag) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(tag)
                .map(set -> set.stream().toList())
                .orElse(List.of());
    }

    public static int count(ServerLevel level) {
        return members(level, ALL).size();
    }

    public static ResourceLocation key(Holder<Structure> hall) {
        return hall.unwrapKey().orElseThrow().location();
    }

    public static String eyeKey(ResourceLocation hall) {
        return hall.getPath().substring(hall.getPath().lastIndexOf('/') + 1);
    }

    public static Item eye(ResourceLocation hall) {
        return BuiltInRegistries.ITEM.get(eyeId(hall));
    }

    public static Component eyeName(ResourceLocation hall) {
        return Component.translatable("item." + eyeId(hall).toLanguageKey());
    }

    private static ResourceLocation eyeId(ResourceLocation hall) {
        return ResourceLocation.fromNamespaceAndPath("endrem", eyeKey(hall) + "_eye");
    }

    private static Component people(ResourceLocation hall) {
        return Library.get().people(hall).<Component>map(book -> Component.literal(book.title())).orElse(eyeName(hall));
    }

    public static void claim(ServerPlayer player, BlockPos stone) {
        if (Memories.touch(player, stone, ItemStack.EMPTY) || Engine.touches(player, stone)) {
            return;
        }
        Optional<ResourceLocation> found = player.level().getBlockEntity(stone) instanceof CallingStoneBlockEntity entity
                ? entity.hall()
                : Optional.empty();
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("wayfarer.hall.silent").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ResourceLocation hall = found.get();
        if (!Engine.asks(player, stone, hall)) {
            take(player, hall);
        }
    }

    public static void take(ServerPlayer player, ResourceLocation hall) {
        if (!Journey.of(player.server).claim(hall)) {
            player.displayClientMessage(Component.translatable("wayfarer.hall.spent", eyeName(hall)).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int taken = Journey.claimed(player.server).size();
        player.displayClientMessage(Component.translatable("wayfarer.hall.count", taken).withStyle(ChatFormatting.GRAY), true);
        player.server.getPlayerList().getPlayers().stream().filter(other -> other != player)
                .forEach(other -> other.sendSystemMessage(Component.translatable("wayfarer.hall.claimed", player.getDisplayName(), eyeName(hall)).withStyle(ChatFormatting.GRAY)));
        if (taken == 1) {
            Talk.notice(Component.translatable("wayfarer.hall.sinks"));
        }
        for (ServerPlayer member : player.server.getPlayerList().getPlayers()) {
            Sounds.play(member, SHARD_TAKEN);
            Shards.sync(member);
            Compass.forget(member);
        }
    }

    @SubscribeEvent
    public static void onReadLectern(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level().getBlockEntity(event.getPos()) instanceof LecternBlockEntity)) {
            return;
        }
        inside(player.serverLevel(), event.getPos()).ifPresent(hall -> {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            openBook(player, hall);
        });
    }

    public static void openBook(ServerPlayer player, ResourceLocation hall) {
        List<Component> pages = new ArrayList<>();
        Optional.ofNullable(Library.get().people().get(hall)).ifPresent(book -> Pages.read(player.server, book));
        Library.get().people(hall).ifPresent(people -> people.pages().forEach(page -> pages.add(Component.literal(page))));
        PacketDistributor.sendToPlayer(player, new BookPayload(pages));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() == MobCategory.MONSTER
                && event.getLevel() instanceof ServerLevel level
                && level.structureManager().getStructureWithPieceAt(event.getPos(), ALL).isValid()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % TITLE_CHECK_TICKS != 0) {
            return;
        }
        ResourceLocation hall = inside(player.serverLevel(), player.blockPosition()).orElse(null);
        ResourceLocation before = hall == null ? INSIDE.remove(player.getUUID()) : INSIDE.put(player.getUUID(), hall);
        if (hall != null && !hall.equals(before) && Firsts.first(player, "hall")) {
            Talk.tell(player, Component.translatable("wayfarer.first.hall"));
        }
        if (hall != null && !hall.equals(before)) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 60, 30));
            player.connection.send(new ClientboundSetSubtitleTextPacket(people(hall)));
            player.connection.send(new ClientboundSetTitleTextPacket(eyeName(hall)));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        INSIDE.remove(event.getEntity().getUUID());
    }
}
