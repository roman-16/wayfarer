package io.github.roman16.wayfarer;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class Leads {
    private static final String[] DIRECTIONS = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
    private static final int CHECK_TICKS = 20;
    private static final int BUILD_BLOCKS = 128;
    private static final int ROUNDING = 100;
    private static final int TRIES = 8;
    private static final String LEAD = "lead";

    private Leads() {}

    public static Optional<Component> give(ServerPlayer player, String id) {
        Library.Lead lead = Library.INSTANCE.leads().get(id);
        Journey.Tale tale = Journey.tale(player);
        Optional<BlockPos> pos = tale.lead(id).map(Journey.Lead::pos).or(() -> locate(player, lead));
        if (pos.isEmpty()) {
            return Optional.empty();
        }
        if (tale.lead(id).isEmpty()) {
            tale.give(id, pos.get());
            Journal.complete(player, lead.givenTask());
        }
        return Optional.of(direction(player.blockPosition(), pos.get()));
    }

    public static Optional<Component> place(ServerPlayer player, ResourceLocation shrine) {
        String id = Library.INSTANCE.places().get(shrine);
        if (id == null) {
            return Optional.empty();
        }
        return give(player, id).map(direction -> Story.fill(Library.INSTANCE.leads().get(id).line(), direction).append("\n\n").append(markLink(id)));
    }

    public static MutableComponent markLink(String id) {
        return link("mark", id);
    }

    private static MutableComponent link(String action, String id) {
        String command = "/" + Wayfarer.MODID + " " + LEAD + " " + action + " " + id;
        return Component.translatable("wayfarer.lead." + action + ".link").withStyle(style -> style
                .withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("wayfarer.lead." + action + ".hover"))));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal(LEAD)
                        .then(Commands.literal("mark").then(leadArgument(true)))
                        .then(Commands.literal("unmark").then(leadArgument(false)))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> leadArgument(boolean marked) {
        return Commands.argument(LEAD, StringArgumentType.greedyString())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(open(context), builder))
                .executes(context -> mark(context, marked));
    }

    private static List<String> open(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return List.of();
        }
        List<String> open = new ArrayList<>();
        Journey.tale(player).leads().forEach((id, lead) -> {
            if (!lead.found()) {
                open.add(id);
            }
        });
        return open.stream().sorted().toList();
    }

    private static int mark(CommandContext<CommandSourceStack> context, boolean marked) {
        ServerPlayer player = context.getSource().getPlayer();
        String id = StringArgumentType.getString(context, LEAD);
        Library.Lead spec = Library.INSTANCE.leads().get(id);
        Journey.Tale tale = player == null ? null : Journey.tale(player);
        if (spec == null || tale == null || tale.lead(id).filter(lead -> !lead.found()).isEmpty()) {
            context.getSource().sendFailure(Component.translatable("wayfarer.lead.untold"));
            return 0;
        }
        tale.mark(id, marked);
        Component name = Component.literal(spec.mark()).withStyle(ChatFormatting.WHITE);
        Component message = Component.translatable("wayfarer.lead." + (marked ? "marked" : "unmarked"), name)
                .withStyle(ChatFormatting.GRAY)
                .append(" ")
                .append(link(marked ? "unmark" : "mark", id));
        Teams.onlineMembers(player).forEach(member -> member.sendSystemMessage(message));
        return 1;
    }

    public static int round(double blocks) {
        return Math.max(ROUNDING, (int) Math.round(blocks / ROUNDING) * ROUNDING);
    }

    public static String heading(int dx, int dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        return DIRECTIONS[Math.floorMod((int) Math.round(degrees / 45), DIRECTIONS.length)];
    }

    public static Component direction(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        String blocks = NumberFormat.getIntegerInstance(Locale.ROOT).format(round(Math.sqrt((double) dx * dx + (double) dz * dz)));
        return Component.translatable("wayfarer.lead.direction", blocks, Component.translatable("wayfarer.direction." + heading(dx, dz)));
    }

    private static Optional<BlockPos> locate(ServerPlayer player, Library.Lead lead) {
        ServerLevel level = player.server.overworld();
        BlockPos from = player.level() == level ? player.blockPosition() : level.getSharedSpawnPos();
        if (!lead.camp().isEmpty()) {
            return Optional.of(campSite(level, from));
        }
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(lead.structures()));
        return Optional.ofNullable(level.findNearestMapStructure(tag, from, Balance.INSTANCE.leads().searchRings(), false));
    }

    private static BlockPos campSite(ServerLevel level, BlockPos from) {
        int distance = Balance.INSTANCE.leads().campBlocks();
        double angle = level.random.nextDouble() * Math.PI * 2;
        BlockPos site = from;
        for (int attempt = 0; attempt < TRIES; attempt++) {
            site = BlockPos.containing(from.getX() + Math.sin(angle) * distance, 0, from.getZ() - Math.cos(angle) * distance);
            Holder<Biome> biome = level.getBiome(site.atY(level.getChunkSource().getGenerator().getSeaLevel()));
            if (!biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH)) {
                return site;
            }
            angle += Math.PI / 4;
        }
        return site;
    }

    public static List<CompassPayload.Marker> markers(ServerPlayer player) {
        List<CompassPayload.Marker> markers = new ArrayList<>();
        Journey.tale(player).leads().forEach((id, lead) -> {
            Library.Lead spec = Library.INSTANCE.leads().get(id);
            if (lead.marked() && !lead.found() && spec != null) {
                markers.add(new CompassPayload.Marker(CompassPayload.Kind.LEAD, spec.mark(), lead.pos().getX(), lead.pos().getZ()));
            }
        });
        return markers;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_TICKS != 0 || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Journey.Tale tale = Journey.tale(player);
        for (Map.Entry<String, Journey.Lead> entry : tale.leads().entrySet()) {
            Journey.Lead lead = entry.getValue();
            Library.Lead spec = Library.INSTANCE.leads().get(entry.getKey());
            if (lead.found() || spec == null) {
                continue;
            }
            double distance = Math.hypot(player.getX() - lead.pos().getX(), player.getZ() - lead.pos().getZ());
            if (!spec.camp().isEmpty()) {
                if (!lead.built() && distance <= BUILD_BLOCKS && level.isLoaded(lead.pos())) {
                    Camps.place(level, ResourceLocation.parse(spec.camp()), lead.pos());
                    tale.build(entry.getKey());
                } else if (lead.built() && distance <= Balance.INSTANCE.leads().campReachBlocks()) {
                    find(player, entry.getKey(), spec);
                }
                continue;
            }
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(spec.structures()));
            if (level.structureManager().getStructureWithPieceAt(player.blockPosition(), tag).isValid()) {
                find(player, entry.getKey(), spec);
            }
        }
    }

    private static void find(ServerPlayer player, String id, Library.Lead lead) {
        Journey.tale(player).find(id);
        Journal.complete(player, lead.foundTask());
        for (ServerPlayer member : Teams.onlineMembers(player)) {
            lead.find().forEach(line -> member.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        }
        for (Library.Item item : lead.items()) {
            ItemStack stack = item.stack();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        if (!lead.page().isEmpty()) {
            Journal.read(player, lead.page());
        }
        Notes.trigger(player, "lead:" + id);
    }
}
