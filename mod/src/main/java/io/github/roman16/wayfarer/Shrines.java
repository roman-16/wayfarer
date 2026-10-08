package io.github.roman16.wayfarer;

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

public final class Shrines {
    public static final TagKey<Structure> ALL = TagKey.create(Registries.STRUCTURE, Wayfarer.id("shrines"));
    public static final TagKey<Structure> TIER_ONE = TagKey.create(Registries.STRUCTURE, Wayfarer.id("shrines/tier_one"));
    public static final TagKey<Structure> TIER_TWO = TagKey.create(Registries.STRUCTURE, Wayfarer.id("shrines/tier_two"));

    private static final int TITLE_CHECK_TICKS = 20;
    private static final int STIRRING_SHARDS = 2;
    private static final String SHARD_TAKEN = "shard_taken";
    private static final Map<UUID, ResourceLocation> INSIDE = new HashMap<>();

    private Shrines() {}

    public static Optional<ResourceLocation> inside(ServerLevel level, BlockPos pos) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, ALL);
        if (!start.isValid() || !(start.getStructure() instanceof ShrineStructure shrine)) {
            return Optional.empty();
        }
        boolean hall = start.getPieces().stream().anyMatch(piece -> piece instanceof ShrinePiece part
                && part.is(shrine.pieces().hall())
                && piece.getBoundingBox().isInside(pos));
        return hall ? Optional.ofNullable(level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(shrine)) : Optional.empty();
    }

    public static boolean inside(ServerPlayer player) {
        return inside(player.serverLevel(), player.blockPosition()).isPresent();
    }

    public static List<Holder<Structure>> tier(ServerLevel level, TagKey<Structure> tier) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(tier)
                .map(set -> set.stream().toList())
                .orElse(List.of());
    }

    public static int count(ServerLevel level) {
        return tier(level, ALL).size();
    }

    public static ResourceLocation key(Holder<Structure> shrine) {
        return shrine.unwrapKey().orElseThrow().location();
    }

    public static String eyeKey(ResourceLocation shrine) {
        return shrine.getPath().substring(shrine.getPath().lastIndexOf('/') + 1);
    }

    public static Item eye(ResourceLocation shrine) {
        return BuiltInRegistries.ITEM.get(eyeId(shrine));
    }

    public static Component eyeName(ResourceLocation shrine) {
        return Component.translatable("item." + eyeId(shrine).toLanguageKey());
    }

    private static ResourceLocation eyeId(ResourceLocation shrine) {
        return ResourceLocation.fromNamespaceAndPath("endrem", eyeKey(shrine) + "_eye");
    }

    private static Component people(ResourceLocation shrine) {
        return Library.INSTANCE.people(shrine).<Component>map(book -> Component.literal(book.title())).orElse(eyeName(shrine));
    }

    public static void claim(ServerPlayer player, BlockPos pedestal) {
        Optional<ResourceLocation> found = player.level().getBlockEntity(pedestal) instanceof ShrinePedestalBlockEntity entity
                ? entity.shrine()
                : Optional.empty();
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("wayfarer.shrine.silent").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ResourceLocation shrine = found.get();
        if (!Journey.of(player.server).claim(shrine)) {
            player.displayClientMessage(Component.translatable("wayfarer.shrine.spent", eyeName(shrine)).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int taken = Journey.claimed(player.server).size();
        String memory = Library.INSTANCE.memory(taken - 1);
        Talk.notice(Component.translatable("wayfarer.shrine.claimed", player.getDisplayName(), eyeName(shrine), taken));
        Library.INSTANCE.book(memory).ifPresent(book -> Talk.notice(Component.translatable("wayfarer.shrine.memory", book.title())));
        if (taken == 1) {
            Talk.notice(Component.translatable("wayfarer.shrine.sinks"));
        }
        if (taken == STIRRING_SHARDS) {
            Talk.notice(Component.translatable("wayfarer.shrine.stirs"));
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
        inside(player.serverLevel(), event.getPos()).ifPresent(shrine -> {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            openBook(player, shrine);
        });
    }

    public static void openBook(ServerPlayer player, ResourceLocation shrine) {
        List<ResourceLocation> claimed = Journey.claimed(player.server);
        int index = claimed.contains(shrine) ? claimed.indexOf(shrine) : claimed.size();
        List<Component> pages = new ArrayList<>();
        Pages.read(player.server, Library.INSTANCE.memory(index));
        Optional.ofNullable(Library.INSTANCE.people().get(shrine)).ifPresent(book -> Pages.read(player.server, book));
        Library.INSTANCE.book(Library.INSTANCE.memory(index)).ifPresent(book -> book.pages().forEach(page -> pages.add(Component.literal(page))));
        Library.INSTANCE.people(shrine).ifPresent(people -> people.pages().forEach(page -> pages.add(Component.literal(page))));
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
        ResourceLocation shrine = inside(player.serverLevel(), player.blockPosition()).orElse(null);
        ResourceLocation before = shrine == null ? INSIDE.remove(player.getUUID()) : INSIDE.put(player.getUUID(), shrine);
        if (shrine != null && !shrine.equals(before) && Firsts.first(player, "shrine")) {
            Talk.tell(player, Component.translatable("wayfarer.first.shrine"));
        }
        if (shrine != null && !shrine.equals(before)) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 60, 30));
            player.connection.send(new ClientboundSetSubtitleTextPacket(people(shrine)));
            player.connection.send(new ClientboundSetTitleTextPacket(eyeName(shrine)));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        INSIDE.remove(event.getEntity().getUUID());
    }
}
