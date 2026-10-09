package io.github.roman16.wayfarer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.roman16.wayfarer.client.Books;
import io.github.roman16.wayfarer.client.Conversation;
import io.github.roman16.wayfarer.client.Flash;
import io.github.roman16.wayfarer.client.JournalScreen;
import io.github.roman16.wayfarer.client.MusicPlayer;
import io.github.roman16.wayfarer.story.Banners;
import io.github.roman16.wayfarer.story.Caravan;
import io.github.roman16.wayfarer.story.Engine;
import io.github.roman16.wayfarer.story.Fallen;
import io.github.roman16.wayfarer.story.Gear;
import io.github.roman16.wayfarer.story.Hold;
import io.github.roman16.wayfarer.story.Journal;
import io.github.roman16.wayfarer.story.Memories;
import io.github.roman16.wayfarer.story.Pages;
import io.github.roman16.wayfarer.story.People;
import io.github.roman16.wayfarer.story.Places;
import io.github.roman16.wayfarer.story.Scenes;
import io.github.roman16.wayfarer.story.Sense;
import io.github.roman16.wayfarer.story.Talk;
import io.github.roman16.wayfarer.story.Tasks;
import io.github.roman16.wayfarer.story.Tollbridge;
import io.github.roman16.wayfarer.story.Wards;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
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
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FireRepair>> FIRE_REPAIR =
            DeferredHolder.create(Registries.RECIPE_SERIALIZER, id("fire_repair"));
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, MODID);
    private static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, MODID);
    private static final DeferredRegister<StructureProcessorType<?>> PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, MODID);
    private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, MODID);

    public static final Supplier<AttachmentType<List<String>>> FIRSTS = ATTACHMENTS.register("firsts",
            () -> AttachmentType.<List<String>>builder(() -> List.of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());
    public static final Supplier<AttachmentType<Integer>> GAZE = ATTACHMENTS.register("gaze",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    public static final Supplier<AttachmentType<CompoundTag>> GRAVE_CURIOS = ATTACHMENTS.register("grave_curios",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());
    public static final Supplier<AttachmentType<GlobalPos>> LAST_FIRE = ATTACHMENTS.register("last_fire",
            () -> AttachmentType.builder(() -> GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO)).serialize(GlobalPos.CODEC).copyOnDeath().build());
    public static final Supplier<AttachmentType<CompoundTag>> STILLNESS = ATTACHMENTS.register("stillness",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    public static final GameRules.Key<GameRules.BooleanValue> STORY =
            GameRules.register(MODID + "Story", GameRules.Category.MISC, GameRules.BooleanValue.create(true));

    public static final DeferredHolder<MobEffect, MobEffect> WARM = EFFECTS.register("warm", Warm::new);
    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<NetherSwap>> NETHER_SWAP =
            PROCESSORS.register("nether_swap", () -> () -> NetherSwap.CODEC);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<Cuts.Loot>> CUT_LOOT =
            LOOT_MODIFIERS.register("cut", () -> Cuts.Loot.CODEC);

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

    public static final DeferredBlock<Hearthstone> HEARTHSTONE = BLOCKS.registerBlock("hearthstone", Hearthstone::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(-1, 3_600_000)
                    .sound(SoundType.STONE));

    public static final DeferredBlock<NotchedLog> NOTCHED_SPRUCE_LOG = BLOCKS.registerBlock("notched_spruce_log", NotchedLog::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PODZOL)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()
                    .noLootTable());

    public static final DeferredBlock<ShrinePedestalBlock> SHRINE_PEDESTAL = BLOCKS.registerBlock("shrine_pedestal", ShrinePedestalBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(-1, 3_600_000)
                    .lightLevel(state -> 7)
                    .sound(SoundType.AMETHYST)
                    .noOcclusion());

    public static final DeferredBlock<TollbridgeBell> TOLLBRIDGE_BELL = BLOCKS.registerBlock("tollbridge_bell", TollbridgeBell::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(-1, 3_600_000)
                    .sound(SoundType.ANVIL)
                    .noOcclusion());
    public static final DeferredBlock<WayfarerSign> WAYFARER_SIGN = BLOCKS.registerBlock("wayfarer_sign", WayfarerSign::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .noCollission()
                    .instabreak()
                    .noLootTable()
                    .sound(SoundType.STONE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShrinePedestalBlockEntity>> SHRINE_PEDESTAL_ENTITY =
            BLOCK_ENTITIES.register("shrine_pedestal", () -> BlockEntityType.Builder.of(ShrinePedestalBlockEntity::new, SHRINE_PEDESTAL.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<Fallen>> FALLEN = ENTITY_TYPES.register("fallen",
            () -> EntityType.Builder.<Fallen>of(Fallen::new, MobCategory.MISC).sized(1.25F, 0.5F).clientTrackingRange(8).build("fallen"));

    public static final DeferredItem<Item> COLD_EMBER = ITEMS.registerSimpleItem("cold_ember", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> EMBER = ITEMS.registerSimpleItem("ember", new Item.Properties().stacksTo(1));

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
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        LOOT_MODIFIERS.register(modBus);
        PIECES.register(modBus);
        if (ModList.get().isLoaded("silentgear")) {
            NeoForge.EVENT_BUS.register(Patterns.class);
            RECIPE_SERIALIZERS.register(FIRE_REPAIR.getId().getPath(), () -> new SimpleCraftingRecipeSerializer<>(FireRepair::new));
            RECIPE_SERIALIZERS.register(modBus);
        }
        PLACEMENTS.register(modBus);
        PROCESSORS.register(modBus);
        STRUCTURES.register(modBus);
        modBus.addListener(Wayfarer::registerPayloads);
        modBus.addListener(Ember::setup);
        if (ModList.get().isLoaded("toughasnails")) {
            modBus.addListener(Heat::setup);
        }
        modBus.addListener(Shards::setup);
        NeoForge.EVENT_BUS.register(Caravan.class);
        NeoForge.EVENT_BUS.register(Clock.class);
        NeoForge.EVENT_BUS.register(Compass.class);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ServerAboutToStartEvent.class, event -> Bundled.forget());
        NeoForge.EVENT_BUS.register(Cuts.class);
        NeoForge.EVENT_BUS.register(Daylight.class);
        NeoForge.EVENT_BUS.register(Ember.class);
        NeoForge.EVENT_BUS.register(Engine.class);
        NeoForge.EVENT_BUS.register(Fire.class);
        NeoForge.EVENT_BUS.register(Firsts.class);
        NeoForge.EVENT_BUS.register(Gate.class);
        NeoForge.EVENT_BUS.register(Gaze.class);
        NeoForge.EVENT_BUS.register(Gear.class);
        NeoForge.EVENT_BUS.register(Graves.class);
        NeoForge.EVENT_BUS.register(Hold.class);
        NeoForge.EVENT_BUS.register(Joining.class);
        NeoForge.EVENT_BUS.register(LastUse.class);
        NeoForge.EVENT_BUS.register(Memories.class);
        NeoForge.EVENT_BUS.register(Music.class);
        NeoForge.EVENT_BUS.register(Nights.class);
        NeoForge.EVENT_BUS.register(Pages.class);
        NeoForge.EVENT_BUS.register(People.class);
        NeoForge.EVENT_BUS.register(Places.class);
        NeoForge.EVENT_BUS.register(Respawn.class);
        NeoForge.EVENT_BUS.register(Rowing.class);
        NeoForge.EVENT_BUS.register(Rules.class);
        NeoForge.EVENT_BUS.register(Scenes.class);
        NeoForge.EVENT_BUS.register(Sense.class);
        NeoForge.EVENT_BUS.register(Shards.class);
        NeoForge.EVENT_BUS.register(Shine.class);
        NeoForge.EVENT_BUS.register(Shrines.class);
        NeoForge.EVENT_BUS.register(Sounds.class);
        Sounds.loop("camp", Caravan::standing);
        Sounds.loop("town_burning", Tollbridge::burning);
        NeoForge.EVENT_BUS.register(Stillness.class);
        NeoForge.EVENT_BUS.register(Talk.class);
        NeoForge.EVENT_BUS.register(Tasks.class);
        NeoForge.EVENT_BUS.register(Tents.class);
        NeoForge.EVENT_BUS.register(Wards.class);
        NeoForge.EVENT_BUS.register(WorldSetup.class);
        if (ModList.get().isLoaded("waystones")) {
            NeoForge.EVENT_BUS.register(Waystone.class);
            Waystone.listen();
        }
        if (ModList.get().isLoaded("gravestone")) {
            NeoForge.EVENT_BUS.register(StoneWatch.class);
        }
        if (ModList.get().isLoaded("hordes")) {
            NeoForge.EVENT_BUS.register(HordeRefuge.class);
        }
        if (ModList.get().isLoaded("nomadictents")) {
            NeoForge.EVENT_BUS.register(TentPitch.class);
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
        registrar.playToClient(Banners.Payload.TYPE, Banners.Payload.STREAM_CODEC, (payload, context) -> HudState.banners.add(payload));
        registrar.playToClient(BookPayload.TYPE, BookPayload.STREAM_CODEC, (payload, context) -> Books.open(payload));
        registrar.playToClient(Talk.Answers.TYPE, Talk.Answers.STREAM_CODEC, (payload, context) -> Conversation.ask(payload));
        registrar.playToServer(Talk.Pick.TYPE, Talk.Pick.STREAM_CODEC, (payload, context) -> Talk.pick((ServerPlayer) context.player(), payload.index()));
        registrar.playToClient(Talk.Subtitle.TYPE, Talk.Subtitle.STREAM_CODEC, (payload, context) -> Conversation.hear(payload));
        registrar.playToServer(Gear.Track.TYPE, Gear.Track.STREAM_CODEC, (payload, context) -> Gear.track((ServerPlayer) context.player(), payload.thing()));
        registrar.playToClient(Journal.View.TYPE, Journal.View.STREAM_CODEC, (payload, context) -> JournalScreen.show(payload.json()));
        registrar.playToServer(Journal.Claim.TYPE, Journal.Claim.STREAM_CODEC,
                (payload, context) -> Engine.claim((ServerPlayer) context.player(), payload.reward()));
        registrar.playToServer(Journal.Dream.TYPE, Journal.Dream.STREAM_CODEC, (payload, context) -> Memories.again((ServerPlayer) context.player(), payload.memory()));
        registrar.playToServer(Journal.Seen.TYPE, Journal.Seen.STREAM_CODEC, (payload, context) -> Engine.seen((ServerPlayer) context.player(), payload.entry()));
        registrar.playToServer(Journal.Track.TYPE, Journal.Track.STREAM_CODEC, (payload, context) -> {
            Engine.track((ServerPlayer) context.player(), payload.quest());
            Tasks.refresh((ServerPlayer) context.player());
        });
        registrar.playToServer(Sense.Payload.TYPE, Sense.Payload.STREAM_CODEC, (payload, context) -> Sense.sense((ServerPlayer) context.player()));
        registrar.playToClient(Tasks.Panel.TYPE, Tasks.Panel.STREAM_CODEC, (payload, context) -> HudState.panel = payload);
        registrar.playToServer(Caravan.HeadIn.TYPE, Caravan.HeadIn.STREAM_CODEC, (payload, context) -> Caravan.headIn((ServerPlayer) context.player()));
        registrar.playToServer(Tasks.Did.TYPE, Tasks.Did.STREAM_CODEC, (payload, context) -> Tasks.told((ServerPlayer) context.player(), payload.task()));
        registrar.playToClient(CompassPayload.TYPE, CompassPayload.STREAM_CODEC, (payload, context) -> HudState.compass = payload);
        registrar.playToClient(GazePayload.TYPE, GazePayload.STREAM_CODEC, (payload, context) -> HudState.gaze = payload);
        registrar.playToClient(NightPayload.TYPE, NightPayload.STREAM_CODEC, (payload, context) -> HudState.night = payload);
        registrar.playToClient(Memories.Flash.TYPE, Memories.Flash.STREAM_CODEC, (payload, context) -> Flash.show(payload));
        registrar.playToClient(Music.Play.TYPE, Music.Play.STREAM_CODEC, (payload, context) -> MusicPlayer.play(payload));
    }
}
