package io.github.roman16.wayfarer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.markusbordihn.easynpc.api.pose.ModelPoseAPI;
import de.markusbordihn.easynpc.data.model.ModelPartType;
import de.markusbordihn.easynpc.data.scale.CustomScale;
import de.markusbordihn.easynpc.data.skin.SkinDataEntry;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.ModelScaleDataCapable;
import de.markusbordihn.easynpc.handler.SkinHandler;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class Workshop {
    private static final ResourceKey<Level> WORKSHOP = ResourceKey.create(Registries.DIMENSION, Wayfarer.id("workshop"));
    private static final int OWNER = 4;
    private static final int GAP = 4;
    private static final int SIGN_DISTANCE = 2;
    private static final int STRUCTURE_REACH = 128 + 16;
    private static final int STANDING_DISTANCE = 8;
    private static final double ROUNDING = 1e-4;
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final String STANDING = "standing";
    private static final JsonObject SKINS = skins();
    private static final List<BoundingBox> designs = new ArrayList<>();
    private static final List<BoundingBox> touched = new ArrayList<>();
    private static int cursor;

    private Workshop() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        designs.clear();
        touched.clear();
        cursor = 0;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("workshop")
                        .requires(source -> source.hasPermission(OWNER))
                        .then(Commands.literal("enter").executes(context -> enter(context.getSource())))
                        .then(adding("place", Kind.PLACE))
                        .then(adding("spawn", Kind.SPAWN)
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .then(Commands.literal("walking").executes(context -> add(context.getSource(),
                                                ResourceLocationArgument.getId(context, "id"), Kind.SPAWN, true)))))
                        .then(Commands.literal("person").then(Commands.argument("skin", StringArgumentType.word())
                                .executes(context -> person(context.getSource(), StringArgumentType.getString(context, "skin"), STANDING))
                                .then(Commands.argument("pose", StringArgumentType.word()).executes(context -> person(context.getSource(),
                                        StringArgumentType.getString(context, "skin"), StringArgumentType.getString(context, "pose"))))))
                        .then(adding("beside", Kind.BESIDE))
                        .then(Commands.literal("bounds").executes(context -> bounds(context.getSource())))
                        .then(Commands.literal("clear").executes(context -> clear(context.getSource())))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adding(String name, Kind kind) {
        return Commands.literal(name).then(Commands.argument("id", ResourceLocationArgument.id())
                .executes(context -> add(context.getSource(), ResourceLocationArgument.getId(context, "id"), kind, false)));
    }

    private static int enter(CommandSourceStack source) {
        ServerLevel level = level(source);
        ServerPlayer player = source.getPlayer();
        if (level == null || player == null) {
            source.sendFailure(Component.literal("workshop failed: the workshop needs a player and the wayfarer:workshop world"));
            return 0;
        }
        BoundingBox all = union();
        double x = all == null ? 0.5 : (all.minX() + all.maxX() + 1) / 2.0;
        double z = (all == null ? 0 : all.maxZ() + 1) + STANDING_DISTANCE + 0.5;
        player.teleportTo(level, x, standing(level), z, 180, 0);
        source.sendSuccess(() -> Component.literal("workshop: entered"), false);
        return 1;
    }

    private static int add(CommandSourceStack source, ResourceLocation id, Kind kind, boolean walking) {
        ServerLevel level = level(source);
        if (level == null) {
            source.sendFailure(Component.literal("workshop failed: there is no wayfarer:workshop world"));
            return 0;
        }
        Optional<EntityType<?>> creature = kind == Kind.PLACE ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        Optional<Holder.Reference<Structure>> structure = kind == Kind.SPAWN ? Optional.empty()
                : level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(ResourceKey.create(Registries.STRUCTURE, id));
        Optional<StructureTemplate> template = kind == Kind.SPAWN ? Optional.empty() : level.getStructureManager().get(id);
        Optional<Block> block = kind == Kind.SPAWN ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id).filter(found -> found != Blocks.AIR);
        Optional<Item> item = kind == Kind.SPAWN ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id).filter(found -> found != Items.AIR);
        BoundingBox box;
        if (creature.isPresent()) {
            box = spawn(level, creature.get(), walking);
        } else if (structure.isPresent()) {
            box = place(level, structure.get().value());
        } else if (template.isPresent()) {
            box = place(level, template.get());
        } else if (block.isPresent()) {
            BlockPos pos = new BlockPos(cursor, standing(level), 0);
            level.setBlockAndUpdate(pos, block.get().defaultBlockState());
            box = new BoundingBox(pos);
        } else if (item.isPresent()) {
            box = show(level, item.get());
        } else {
            String wanted = switch (kind) {
                case PLACE -> "structure, template, block or item";
                case SPAWN -> "creature";
                case BESIDE -> "creature, structure, template, block or item";
            };
            source.sendFailure(Component.literal("workshop failed: there is no " + wanted + " called " + id));
            return 0;
        }
        if (box == null) {
            source.sendFailure(Component.literal("workshop failed: " + id + " could not be built in the workshop"));
            return 0;
        }
        record(level, box, id);
        source.sendSuccess(() -> Component.literal("workshop: %s at %d %d %d, %d by %d by %d blocks".formatted(id, box.minX(), box.minY(),
                box.minZ(), box.getXSpan(), box.getYSpan(), box.getZSpan())), false);
        return 1;
    }

    private static int person(CommandSourceStack source, String skin, String pose) {
        ServerLevel level = level(source);
        if (level == null) {
            source.sendFailure(Component.literal("workshop failed: there is no wayfarer:workshop world"));
            return 0;
        }
        JsonObject look = SKINS.getAsJsonObject(skin);
        if (look == null) {
            source.sendFailure(Component.literal("workshop failed: generator/skins.toml has no " + skin + "; it has " + SKINS.keySet()));
            return 0;
        }
        Entity body = EntityType.byString(look.get("model").getAsString()).map(type -> type.create(level)).orElse(null);
        if (!(body instanceof EasyNPC<?> npc) || !(body instanceof ModelScaleDataCapable<?> scaled)) {
            source.sendFailure(Component.literal("workshop failed: " + look.get("model").getAsString() + " is not an Easy NPC body"));
            return 0;
        }
        body.moveTo(cursor + 1, standing(level), 1, 0, 0);
        body.setYHeadRot(0);
        body.setInvulnerable(true);
        if (body instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        level.addFreshEntity(body);
        SkinHandler.setSkin(npc, SkinDataEntry.createResourceLocationSkin(Wayfarer.id("textures/entity/people/" + skin + ".png")));
        scaled.setModelPartScale(ModelPartType.ROOT, new CustomScale(look.get("scale").getAsFloat()));
        if (!pose.equals(STANDING) && !ModelPoseAPI.setPose(npc, ResourceLocation.fromNamespaceAndPath("easy_npc", "pose/humanoid/" + pose))) {
            body.discard();
            source.sendFailure(Component.literal("workshop failed: Easy NPC has no pose " + pose));
            return 0;
        }
        BlockPos at = body.blockPosition();
        BoundingBox box = new BoundingBox(at.getX() - 1, at.getY(), at.getZ() - 1, at.getX() + 1, at.getY() + 1, at.getZ() + 1);
        record(level, box, Wayfarer.id("people/" + skin));
        source.sendSuccess(() -> Component.literal("workshop: %s %s at %d %d %d".formatted(skin, pose, at.getX(), at.getY(), at.getZ())), false);
        return 1;
    }

    private static BoundingBox spawn(ServerLevel level, EntityType<?> type, boolean walking) {
        Entity entity = type.create(level);
        if (entity == null) {
            return null;
        }
        entity.moveTo(cursor + entity.getBbWidth() / 2, standing(level), entity.getBbWidth() / 2, 0, 0);
        entity.setYHeadRot(0);
        entity.setInvulnerable(true);
        if (entity instanceof Mob mob) {
            mob.setNoAi(!walking);
            mob.setPersistenceRequired();
        }
        level.addFreshEntity(entity);
        AABB outline = entity.getBoundingBox();
        return new BoundingBox(block(outline.minX), block(outline.minY), block(outline.minZ),
                block(outline.maxX - ROUNDING), block(outline.maxY - ROUNDING), block(outline.maxZ - ROUNDING));
    }

    private static BoundingBox place(ServerLevel level, Structure structure) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BlockPos centre = new BlockPos(cursor + STRUCTURE_REACH, standing(level), 0);
        StructureStart start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), new ChunkPos(centre), 0, level,
                biome -> true);
        if (!start.isValid()) {
            return null;
        }
        BoundingBox box = start.getBoundingBox();
        ChunkPos low = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos high = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        ChunkPos.rangeClosed(low, high).forEach(chunk -> {
            level.getChunk(chunk.x, chunk.z);
            start.placeInChunk(level, level.structureManager(), generator, level.getRandom(), new BoundingBox(chunk.getMinBlockX(),
                    level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk);
        });
        return box;
    }

    private static BoundingBox place(ServerLevel level, StructureTemplate template) {
        BlockPos pos = new BlockPos(cursor, standing(level), 0);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        BoundingBox box = template.getBoundingBox(settings, pos);
        ChunkPos.rangeClosed(new ChunkPos(new BlockPos(box.minX(), 0, box.minZ())), new ChunkPos(new BlockPos(box.maxX(), 0, box.maxZ())))
                .forEach(chunk -> level.getChunk(chunk.x, chunk.z));
        return template.placeInWorld(level, pos, pos, settings, level.getRandom(), Block.UPDATE_CLIENTS) ? box : null;
    }

    private static BoundingBox show(ServerLevel level, Item item) {
        Display.ItemDisplay display = EntityType.ITEM_DISPLAY.create(level);
        if (display == null) {
            return null;
        }
        display.getSlot(0).set(new ItemStack(item));
        display.moveTo(cursor + 0.5, standing(level) + 0.5, 0.5, 0, 0);
        level.addFreshEntity(display);
        return new BoundingBox(new BlockPos(cursor, standing(level), 0));
    }

    private static void record(ServerLevel level, BoundingBox box, ResourceLocation id) {
        designs.add(box);
        touched.add(box);
        BlockPos sign = new BlockPos(box.minX(), standing(level), box.maxZ() + SIGN_DISTANCE);
        level.setBlockAndUpdate(sign, Blocks.OAK_SIGN.defaultBlockState());
        if (level.getBlockEntity(sign) instanceof SignBlockEntity entity) {
            entity.setText(new SignText()
                    .setMessage(1, Component.literal(id.getNamespace()))
                    .setMessage(2, Component.literal(id.getPath())), true);
            entity.setWaxed(true);
        }
        touched.add(new BoundingBox(sign));
        cursor = Math.max(cursor, box.maxX() + 1 + GAP);
    }

    private static int bounds(CommandSourceStack source) {
        BoundingBox all = union();
        if (all == null) {
            source.sendFailure(Component.literal("workshop failed: the workshop is empty"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("workshop bounds: %d %d %d %d %d %d".formatted(all.minX(), all.minY(), all.minZ(),
                all.maxX(), all.maxY(), all.maxZ())), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        ServerLevel level = level(source);
        if (level == null) {
            source.sendFailure(Component.literal("workshop failed: there is no wayfarer:workshop world"));
            return 0;
        }
        List<BlockState> layers = ((FlatLevelSource) level.getChunkSource().getGenerator()).settings().getLayers();
        removeEntities(level);
        for (BoundingBox box : touched) {
            BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()).forEach(pos -> {
                if (level.getBlockEntity(pos) instanceof Container container) {
                    container.clearContent();
                }
                int layer = pos.getY() - level.getMinBuildHeight();
                level.setBlock(pos, layer < layers.size() ? layers.get(layer) : Blocks.AIR.defaultBlockState(), QUIET);
            });
        }
        removeEntities(level);
        designs.clear();
        touched.clear();
        cursor = 0;
        source.sendSuccess(() -> Component.literal("workshop: cleared"), false);
        return 1;
    }

    private static void removeEntities(ServerLevel level) {
        List<Entity> entities = new ArrayList<>();
        level.getAllEntities().forEach(entity -> {
            if (!(entity instanceof Player)) {
                entities.add(entity);
            }
        });
        entities.forEach(Entity::discard);
    }

    private static BoundingBox union() {
        return BoundingBox.encapsulatingBoxes(designs).orElse(null);
    }

    private static int block(double coordinate) {
        return (int) Math.floor(coordinate + ROUNDING);
    }

    private static ServerLevel level(CommandSourceStack source) {
        return source.getServer().getLevel(WORKSHOP);
    }

    private static int standing(ServerLevel level) {
        return level.getMinBuildHeight() + ((FlatLevelSource) level.getChunkSource().getGenerator()).settings().getLayers().size();
    }

    private static JsonObject skins() {
        try (var stream = Workshop.class.getResourceAsStream("/wayfarer/skins.json")) {
            if (stream == null) {
                throw new IllegalStateException("skins.json is missing; run just generate");
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private enum Kind {
        PLACE,
        SPAWN,
        BESIDE
    }
}
