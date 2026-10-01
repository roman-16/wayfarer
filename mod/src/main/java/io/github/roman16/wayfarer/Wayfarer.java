package io.github.roman16.wayfarer;

import com.mojang.serialization.Codec;
import io.github.roman16.wayfarer.client.Books;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(Wayfarer.MODID)
public final class Wayfarer {
    public static final String MODID = "wayfarer";

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, MODID);
    private static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, MODID);
    private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, MODID);

    public static final Supplier<AttachmentType<Integer>> EYE_STEPS = ATTACHMENTS.register("eye_steps",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    public static final Supplier<AttachmentType<List<String>>> FIRSTS = ATTACHMENTS.register("firsts",
            () -> AttachmentType.<List<String>>builder(() -> List.of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());
    public static final Supplier<AttachmentType<Integer>> GAZE = ATTACHMENTS.register("gaze",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    public static final Supplier<AttachmentType<CompoundTag>> GRAVE_CURIOS = ATTACHMENTS.register("grave_curios",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());
    public static final Supplier<AttachmentType<Boolean>> JOURNAL_GIVEN = ATTACHMENTS.register("journal_given",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());
    public static final Supplier<AttachmentType<CompoundTag>> STILLNESS = ATTACHMENTS.register("stillness",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    public static final DeferredHolder<MobEffect, MobEffect> WARM = EFFECTS.register("warm", Warm::new);

    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<ExactRingPlacement>> EXACT_RING =
            PLACEMENTS.register("exact_ring", () -> () -> ExactRingPlacement.CODEC);

    public static final DeferredHolder<StructureType<?>, StructureType<ShrineStructure>> SHRINE =
            STRUCTURES.register("shrine", () -> () -> ShrineStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> ENTRANCE_PIECE =
            PIECES.register("entrance", () -> (StructurePieceType.StructureTemplateType) EntrancePiece::new);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> SHRINE_PIECE =
            PIECES.register("shrine", () -> (StructurePieceType.StructureTemplateType) ShrinePiece::new);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> SHAFT_PIECE =
            PIECES.register("shaft", () -> ShaftPiece::new);

    public static final DeferredBlock<ShrinePedestalBlock> SHRINE_PEDESTAL = BLOCKS.registerBlock("shrine_pedestal", ShrinePedestalBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(-1, 3_600_000)
                    .lightLevel(state -> 7)
                    .sound(SoundType.AMETHYST)
                    .noOcclusion());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShrinePedestalBlockEntity>> SHRINE_PEDESTAL_ENTITY =
            BLOCK_ENTITIES.register("shrine_pedestal", () -> BlockEntityType.Builder.of(ShrinePedestalBlockEntity::new, SHRINE_PEDESTAL.get()).build(null));

    public static final List<DeferredItem<Item>> VEILS = IntStream.rangeClosed(1, 3)
            .mapToObj(level -> ITEMS.registerSimpleItem("ember_veil_" + level, new Item.Properties().stacksTo(1).rarity(Rarity.values()[level])))
            .toList();

    static {
        ITEMS.registerSimpleBlockItem(SHRINE_PEDESTAL);
    }

    public Wayfarer(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        EFFECTS.register(modBus);
        ITEMS.register(modBus);
        PIECES.register(modBus);
        PLACEMENTS.register(modBus);
        STRUCTURES.register(modBus);
        modBus.addListener(Wayfarer::registerPayloads);
        modBus.addListener(Shards::setup);
        NeoForge.EVENT_BUS.register(Compass.class);
        NeoForge.EVENT_BUS.register(Daylight.class);
        NeoForge.EVENT_BUS.register(Difficulty.class);
        NeoForge.EVENT_BUS.register(Ember.class);
        NeoForge.EVENT_BUS.register(Fire.class);
        NeoForge.EVENT_BUS.register(Gate.class);
        NeoForge.EVENT_BUS.register(Gaze.class);
        NeoForge.EVENT_BUS.register(Graves.class);
        NeoForge.EVENT_BUS.register(Journal.class);
        NeoForge.EVENT_BUS.register(Leads.class);
        NeoForge.EVENT_BUS.register(Maren.class);
        NeoForge.EVENT_BUS.register(Nights.class);
        NeoForge.EVENT_BUS.register(Notes.class);
        NeoForge.EVENT_BUS.register(Rowing.class);
        NeoForge.EVENT_BUS.register(Rules.class);
        NeoForge.EVENT_BUS.register(Shards.class);
        NeoForge.EVENT_BUS.register(Shrines.class);
        NeoForge.EVENT_BUS.register(Stillness.class);
        NeoForge.EVENT_BUS.register(Story.class);
        NeoForge.EVENT_BUS.register(Tents.class);
        NeoForge.EVENT_BUS.register(WorldSetup.class);
        if (ModList.get().isLoaded("hordes")) {
            NeoForge.EVENT_BUS.register(HordeRefuge.class);
        }
        if (ModList.get().isLoaded("lootr")) {
            NeoForge.EVENT_BUS.register(Loot.class);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BookPayload.TYPE, BookPayload.STREAM_CODEC, (payload, context) -> Books.open(payload));
        registrar.playToClient(CompassPayload.TYPE, CompassPayload.STREAM_CODEC, (payload, context) -> HudState.compass = payload);
        registrar.playToClient(GazePayload.TYPE, GazePayload.STREAM_CODEC, (payload, context) -> HudState.gaze = payload);
        registrar.playToClient(NightPayload.TYPE, NightPayload.STREAM_CODEC, (payload, context) -> HudState.night = payload);
    }
}
