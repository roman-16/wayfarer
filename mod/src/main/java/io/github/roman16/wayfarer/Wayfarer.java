package io.github.roman16.wayfarer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.roman16.wayfarer.client.Books;
import io.github.roman16.wayfarer.client.Conversation;
import io.github.roman16.wayfarer.client.Ending;
import io.github.roman16.wayfarer.client.Flash;
import io.github.roman16.wayfarer.client.JournalScreen;
import io.github.roman16.wayfarer.client.MusicPlayer;
import io.github.roman16.wayfarer.client.PickUps;
import io.github.roman16.wayfarer.client.Recap;
import io.github.roman16.wayfarer.story.After;
import io.github.roman16.wayfarer.story.Afterwards;
import io.github.roman16.wayfarer.story.Banners;
import io.github.roman16.wayfarer.story.Bell;
import io.github.roman16.wayfarer.story.CaptainsPages;
import io.github.roman16.wayfarer.story.Caravan;
import io.github.roman16.wayfarer.story.Deeds;
import io.github.roman16.wayfarer.story.Dice;
import io.github.roman16.wayfarer.story.Duel;
import io.github.roman16.wayfarer.story.Engine;
import io.github.roman16.wayfarer.story.Errands;
import io.github.roman16.wayfarer.story.Fallen;
import io.github.roman16.wayfarer.story.Frost;
import io.github.roman16.wayfarer.story.Gear;
import io.github.roman16.wayfarer.story.Hold;
import io.github.roman16.wayfarer.story.Journal;
import io.github.roman16.wayfarer.story.Locals;
import io.github.roman16.wayfarer.story.Marks;
import io.github.roman16.wayfarer.story.Memories;
import io.github.roman16.wayfarer.story.Mine;
import io.github.roman16.wayfarer.story.Pages;
import io.github.roman16.wayfarer.story.People;
import io.github.roman16.wayfarer.story.Places;
import io.github.roman16.wayfarer.story.Robes;
import io.github.roman16.wayfarer.story.SandSingers;
import io.github.roman16.wayfarer.story.Scenes;
import io.github.roman16.wayfarer.story.Sense;
import io.github.roman16.wayfarer.story.Stepping;
import io.github.roman16.wayfarer.story.Talk;
import io.github.roman16.wayfarer.story.Tasks;
import io.github.roman16.wayfarer.story.Tollbridge;
import io.github.roman16.wayfarer.story.Wards;
import io.github.roman16.wayfarer.worm.GreatWorms;
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
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.DensityFunction;
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
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
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
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS = DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MODID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    private static final DeferredRegister<GameEvent> GAME_EVENTS = DeferredRegister.create(Registries.GAME_EVENT, MODID);
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

    public static final DeferredHolder<GameEvent, GameEvent> VOICE = GAME_EVENTS.register("voice", () -> new GameEvent(GameEvent.DEFAULT_NOTIFICATION_RADIUS));
    public static final DeferredHolder<MobEffect, MobEffect> WARM = EFFECTS.register("warm", Warm::new);
    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<NetherSwap>> NETHER_SWAP =
            PROCESSORS.register("nether_swap", () -> () -> NetherSwap.CODEC);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ContainerLoot>> CONTAINER_LOOT =
            LOOT_MODIFIERS.register("container_loot", () -> ContainerLoot.CODEC);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<CaptainsPages>> CAPTAINS_PAGES =
            LOOT_MODIFIERS.register("captains_pages", () -> CaptainsPages.CODEC);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<Cuts.Loot>> CUT_LOOT =
            LOOT_MODIFIERS.register("cut", () -> Cuts.Loot.CODEC);

    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<BandPlacement>> BAND =
            PLACEMENTS.register("band", () -> () -> BandPlacement.CODEC);
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<ExactRingPlacement>> EXACT_RING =
            PLACEMENTS.register("exact_ring", () -> () -> ExactRingPlacement.CODEC);
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<WorldShape>> WORLD_SHAPE =
            DENSITY_FUNCTIONS.register("world_shape", () -> WorldShape.CODEC);

    public static final DeferredHolder<StructureType<?>, StructureType<HallStructure>> HALL =
            STRUCTURES.register("hall", () -> () -> HallStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> ENTRANCE_PIECE =
            PIECES.register("entrance", () -> (StructurePieceType.StructureTemplateType) EntrancePiece::new);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> HALL_PIECE =
            PIECES.register("hall", () -> (StructurePieceType.StructureTemplateType) HallPiece::new);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> SHAFT_PIECE =
            PIECES.register("shaft", () -> ShaftPiece::new);

    public static final DeferredBlock<Block> FALSE_WALL = BLOCKS.registerSimpleBlock("false_wall",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(1)
                    .sound(SoundType.PACKED_MUD)
                    .noCollission()
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .noLootTable());

    public static final DeferredBlock<FrostIronOre> FROST_IRON_ORE = BLOCKS.registerBlock("frost_iron_ore", FrostIronOre::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(4.5f, 3)
                    .sound(SoundType.DEEPSLATE));

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

    public static final DeferredBlock<CallingStoneBlock> CALLING_STONE = BLOCKS.registerBlock("calling_stone", CallingStoneBlock::new,
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
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CallingStoneBlockEntity>> CALLING_STONE_ENTITY =
            BLOCK_ENTITIES.register("calling_stone", () -> BlockEntityType.Builder.of(CallingStoneBlockEntity::new, CALLING_STONE.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<Deep>> DEEP = ENTITY_TYPES.register("deep",
            () -> EntityType.Builder.<Deep>of(Deep::new, MobCategory.MONSTER).sized(0.85F, 0.85F).eyeHeight(0.425F).clientTrackingRange(16).build("deep"));
    public static final DeferredHolder<EntityType<?>, EntityType<Fallen>> FALLEN = ENTITY_TYPES.register("fallen",
            () -> EntityType.Builder.<Fallen>of(Fallen::new, MobCategory.MISC).sized(1.25F, 0.5F).clientTrackingRange(8).build("fallen"));

    public static final DeferredItem<Item> COLD_EMBER = ITEMS.registerSimpleItem("cold_ember", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> EMBER = ITEMS.registerSimpleItem("ember", new Item.Properties().stacksTo(1));
    public static final DeferredItem<RawFrostIron> RAW_FROST_IRON = ITEMS.registerItem("raw_frost_iron", RawFrostIron::new);

    public static final List<DeferredItem<Item>> VEILS = IntStream.rangeClosed(1, 3)
            .mapToObj(level -> ITEMS.registerSimpleItem("ember_veil_" + level, new Item.Properties().stacksTo(1).rarity(Rarity.values()[level])))
            .toList();

    static {
        ITEMS.registerSimpleBlockItem(FROST_IRON_ORE);
        ITEMS.registerSimpleBlockItem(CALLING_STONE);
    }

    public Wayfarer(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        EFFECTS.register(modBus);
        ENTITY_TYPES.register(modBus);
        GAME_EVENTS.register(modBus);
        ITEMS.register(modBus);
        LOOT_MODIFIERS.register(modBus);
        PIECES.register(modBus);
        if (ModList.get().isLoaded("geckolib")) {
            GreatWorms.register(modBus);
        }
        if (ModList.get().isLoaded("silentgear")) {
            NeoForge.EVENT_BUS.register(Patterns.class);
            RECIPE_SERIALIZERS.register(FIRE_REPAIR.getId().getPath(), () -> new SimpleCraftingRecipeSerializer<>(FireRepair::new));
            RECIPE_SERIALIZERS.register(modBus);
        }
        PLACEMENTS.register(modBus);
        DENSITY_FUNCTIONS.register(modBus);
        PROCESSORS.register(modBus);
        STRUCTURES.register(modBus);
        modBus.addListener(Wayfarer::registerPayloads);
        modBus.addListener(RegisterConfigurationTasksEvent.class, event -> event.register(new WorldSetup.Telling(event.getListener())));
        modBus.addListener(EntityAttributeCreationEvent.class, event -> event.put(DEEP.get(), Guardian.createAttributes().build()));
        modBus.addListener(Ember::setup);
        if (ModList.get().isLoaded("toughasnails")) {
            modBus.addListener(Heat::setup);
        }
        modBus.addListener(Shards::setup);
        NeoForge.EVENT_BUS.register(After.class);
        NeoForge.EVENT_BUS.register(Bell.class);
        NeoForge.EVENT_BUS.register(Burning.class);
        NeoForge.EVENT_BUS.register(Caravan.class);
        NeoForge.EVENT_BUS.register(Clock.class);
        NeoForge.EVENT_BUS.register(Compass.class);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ServerAboutToStartEvent.class, event -> Bundled.forget());
        if (ModList.get().isLoaded("distanthorizons")) {
            NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, event -> FarViewServer.limit());
        }
        NeoForge.EVENT_BUS.register(Cuts.class);
        NeoForge.EVENT_BUS.register(Daylight.class);
        NeoForge.EVENT_BUS.register(Deeds.class);
        NeoForge.EVENT_BUS.register(Dice.class);
        NeoForge.EVENT_BUS.register(Dragon.class);
        NeoForge.EVENT_BUS.register(Duel.class);
        NeoForge.EVENT_BUS.register(Ember.class);
        NeoForge.EVENT_BUS.register(Engine.class);
        NeoForge.EVENT_BUS.register(Errands.class);
        NeoForge.EVENT_BUS.register(Fire.class);
        NeoForge.EVENT_BUS.register(Firsts.class);
        NeoForge.EVENT_BUS.register(Frost.class);
        NeoForge.EVENT_BUS.register(FrostIronOre.class);
        NeoForge.EVENT_BUS.register(Gate.class);
        NeoForge.EVENT_BUS.register(Gaze.class);
        NeoForge.EVENT_BUS.register(Gear.class);
        NeoForge.EVENT_BUS.register(Graves.class);
        NeoForge.EVENT_BUS.register(Hold.class);
        NeoForge.EVENT_BUS.register(Joining.class);
        NeoForge.EVENT_BUS.register(LastUse.class);
        NeoForge.EVENT_BUS.register(Locals.class);
        NeoForge.EVENT_BUS.register(Memories.class);
        NeoForge.EVENT_BUS.register(Mine.class);
        NeoForge.EVENT_BUS.register(Music.class);
        NeoForge.EVENT_BUS.register(Nights.class);
        NeoForge.EVENT_BUS.register(Pages.class);
        NeoForge.EVENT_BUS.register(People.class);
        NeoForge.EVENT_BUS.register(Places.class);
        NeoForge.EVENT_BUS.register(Respawn.class);
        NeoForge.EVENT_BUS.register(Rowing.class);
        NeoForge.EVENT_BUS.register(Robes.class);
        NeoForge.EVENT_BUS.register(Rules.class);
        NeoForge.EVENT_BUS.register(SandSingers.class);
        NeoForge.EVENT_BUS.register(Scenes.class);
        NeoForge.EVENT_BUS.register(Sea.class);
        NeoForge.EVENT_BUS.register(Sense.class);
        NeoForge.EVENT_BUS.register(Shards.class);
        NeoForge.EVENT_BUS.register(Shine.class);
        NeoForge.EVENT_BUS.register(Halls.class);
        NeoForge.EVENT_BUS.register(Sorting.class);
        NeoForge.EVENT_BUS.register(Sounds.class);
        Sounds.loop("camp", Caravan::standing);
        Sounds.loop("town_burning", Tollbridge::burning);
        NeoForge.EVENT_BUS.register(Stare.class);
        NeoForge.EVENT_BUS.register(Stepping.class);
        NeoForge.EVENT_BUS.register(Veils.class);
        NeoForge.EVENT_BUS.register(Stillness.class);
        NeoForge.EVENT_BUS.register(Talk.class);
        NeoForge.EVENT_BUS.register(Tasks.class);
        NeoForge.EVENT_BUS.register(Tents.class);
        NeoForge.EVENT_BUS.register(Wards.class);
        NeoForge.EVENT_BUS.register(Weather.class);
        NeoForge.EVENT_BUS.register(WorldSetup.class);
        if (ModList.get().isLoaded("waystones")) {
            NeoForge.EVENT_BUS.register(Waystone.class);
            Waystone.listen();
        }
        if (ModList.get().isLoaded("gravestone")) {
            NeoForge.EVENT_BUS.register(StoneWatch.class);
        }
        if (ModList.get().isLoaded("hordes")) {
            NeoForge.EVENT_BUS.register(Hordes.class);
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
        registrar.configurationToClient(WorldSetup.WorldId.TYPE, WorldSetup.WorldId.STREAM_CODEC, (payload, context) -> Recap.world(payload.id()));
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
        registrar.playToServer(Sorting.Looking.TYPE, Sorting.Looking.STREAM_CODEC, (payload, context) -> Sorting.look((ServerPlayer) context.player(), payload.open()));
        registrar.playToClient(Tasks.Panel.TYPE, Tasks.Panel.STREAM_CODEC, (payload, context) -> HudState.panel = payload);
        registrar.playToClient(Caravan.Smoke.TYPE, Caravan.Smoke.STREAM_CODEC, (payload, context) -> HudState.smoke = payload.fire());
        registrar.playToServer(Caravan.HeadIn.TYPE, Caravan.HeadIn.STREAM_CODEC, (payload, context) -> Caravan.headIn((ServerPlayer) context.player()));
        registrar.playToServer(Tasks.Did.TYPE, Tasks.Did.STREAM_CODEC, (payload, context) -> Tasks.told((ServerPlayer) context.player(), payload.task()));
        registrar.playToServer(Marks.Mark.TYPE, Marks.Mark.STREAM_CODEC, (payload, context) -> Marks.mark((ServerPlayer) context.player(), payload.at()));
        registrar.playToServer(Record.Frames.TYPE, Record.Frames.STREAM_CODEC, (payload, context) -> Record.frames((ServerPlayer) context.player(), payload));
        registrar.playToClient(CompassPayload.TYPE, CompassPayload.STREAM_CODEC, (payload, context) -> HudState.compass = payload);
        registrar.playToClient(GazePayload.TYPE, GazePayload.STREAM_CODEC, (payload, context) -> HudState.gaze = payload);
        registrar.playToClient(NightPayload.TYPE, NightPayload.STREAM_CODEC, (payload, context) -> HudState.night = payload);
        registrar.playToClient(After.Ending.TYPE, After.Ending.STREAM_CODEC, (payload, context) -> Ending.show(payload.afterwards()));
        registrar.playToClient(Afterwards.Open.TYPE, Afterwards.Open.STREAM_CODEC, (payload, context) -> JournalScreen.afterwards());
        registrar.playToClient(After.Skin.TYPE, After.Skin.STREAM_CODEC, (payload, context) -> Ending.skin(payload.player(), payload.texture()));
        registrar.playToClient(Memories.Flash.TYPE, Memories.Flash.STREAM_CODEC, (payload, context) -> Flash.show(payload));
        registrar.playToClient(Music.Play.TYPE, Music.Play.STREAM_CODEC, (payload, context) -> MusicPlayer.play(payload));
        registrar.playToClient(Notices.Given.TYPE, Notices.Given.STREAM_CODEC, (payload, context) -> PickUps.given(payload));
        registrar.playToClient(Notices.Written.TYPE, Notices.Written.STREAM_CODEC, (payload, context) -> PickUps.written(payload));
    }
}
