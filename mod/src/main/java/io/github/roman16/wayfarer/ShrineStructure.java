package io.github.roman16.wayfarer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class ShrineStructure extends Structure {
    public record Layout(int cover, BlockPos entrance, int maxSteps, BlockPos mouth, BlockPos shaft, BlockPos stair) {
        static final Codec<Layout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.INT.fieldOf("cover").forGetter(Layout::cover),
                        BlockPos.CODEC.fieldOf("entrance").forGetter(Layout::entrance),
                        Codec.INT.fieldOf("max_steps").forGetter(Layout::maxSteps),
                        BlockPos.CODEC.fieldOf("mouth").forGetter(Layout::mouth),
                        BlockPos.CODEC.fieldOf("shaft").forGetter(Layout::shaft),
                        BlockPos.CODEC.fieldOf("stair").forGetter(Layout::stair))
                .apply(instance, Layout::new));
    }

    public record Pieces(
            ResourceLocation cap,
            ResourceLocation hall,
            ResourceLocation marker,
            ResourceLocation openStep,
            ResourceLocation seal,
            ResourceLocation shaft,
            ResourceLocation step) {
        static final Codec<Pieces> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("cap").forGetter(Pieces::cap),
                        ResourceLocation.CODEC.fieldOf("hall").forGetter(Pieces::hall),
                        ResourceLocation.CODEC.fieldOf("marker").forGetter(Pieces::marker),
                        ResourceLocation.CODEC.fieldOf("open_step").forGetter(Pieces::openStep),
                        ResourceLocation.CODEC.fieldOf("seal").forGetter(Pieces::seal),
                        ResourceLocation.CODEC.fieldOf("shaft").forGetter(Pieces::shaft),
                        ResourceLocation.CODEC.fieldOf("step").forGetter(Pieces::step))
                .apply(instance, Pieces::new));
    }

    public static final MapCodec<ShrineStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    settingsCodec(instance),
                    Layout.CODEC.fieldOf("layout").forGetter(ShrineStructure::layout),
                    Pieces.CODEC.fieldOf("pieces").forGetter(ShrineStructure::pieces))
            .apply(instance, ShrineStructure::new));

    private record Column(int floor, boolean wet) {}

    private record Plan(Rotation rotation, BlockPos origin, List<Boolean> closed, Column light, BlockPos mouth) {}

    private final Layout layout;
    private final Pieces pieces;

    public ShrineStructure(StructureSettings settings, Layout layout, Pieces pieces) {
        super(settings);
        this.layout = layout;
        this.pieces = pieces;
    }

    public Layout layout() {
        return layout;
    }

    public Pieces pieces() {
        return pieces;
    }

    @Override
    public StructureType<?> type() {
        return Wayfarer.SHRINE.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        BlockPos center = context.chunkPos().getMiddleBlockPosition(0);
        BlockPos surface = center.atY(column(context, center).floor());
        var biome = context.biomeSource().getNoiseBiome(
                QuartPos.fromBlock(surface.getX()),
                QuartPos.fromBlock(surface.getY()),
                QuartPos.fromBlock(surface.getZ()),
                context.randomState().sampler());
        if (!context.validBiome().test(biome)) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(surface, builder -> build(builder, context, plan(context, center))));
    }

    private Plan plan(GenerationContext context, BlockPos center) {
        StructureTemplateManager templates = context.structureTemplateManager();
        Vec3i hall = templates.getOrCreate(pieces.hall()).getSize();
        int ceiling = templates.getOrCreate(pieces.step()).getSize().getY() - 1 - layout.stair().getY();
        Plan best = null;
        for (Rotation rotation : Rotation.values()) {
            Plan plan = plan(context, rotation, hall, ceiling, center);
            if (best == null || plan.closed().size() < best.closed().size()) {
                best = plan;
            }
        }
        return best;
    }

    private Plan plan(GenerationContext context, Rotation rotation, Vec3i hall, int ceiling, BlockPos center) {
        BlockPos corner = center.subtract(turn(new BlockPos(hall.getX() / 2, 0, hall.getZ() / 2), rotation));
        List<BlockPos> footprint = List.of(
                BlockPos.ZERO,
                new BlockPos(hall.getX() - 1, 0, 0),
                new BlockPos(0, 0, hall.getZ() - 1),
                new BlockPos(hall.getX() - 1, 0, hall.getZ() - 1),
                new BlockPos(hall.getX() / 2, 0, hall.getZ() / 2));
        int lowest = footprint.stream()
                .mapToInt(local -> column(context, place(corner, local, rotation)).floor())
                .min()
                .orElseThrow();
        Column light = column(context, place(corner, layout.shaft(), rotation));
        int floor = Math.max(lowest - hall.getY() - layout.cover(), context.heightAccessor().getMinBuildHeight() + 1);
        BlockPos origin = corner.atY(floor);
        List<Boolean> closed = new ArrayList<>();
        for (int step = 0; step < layout.maxSteps(); step++) {
            BlockPos stair = layout.entrance().offset(0, step, step);
            Column ground = column(context, place(origin, stair, rotation));
            int stairY = floor + stair.getY();
            if (!ground.wet() && stairY >= ground.floor() - 1) {
                closed.add(false);
                break;
            }
            closed.add(ground.wet() || stairY + ceiling < ground.floor());
        }
        BlockPos last = layout.entrance().offset(0, closed.size() - 1, closed.size());
        BlockPos mouth = place(origin, last, rotation);
        return new Plan(rotation, origin, closed, light, mouth.atY(column(context, mouth).floor()));
    }

    private void build(StructurePiecesBuilder builder, GenerationContext context, Plan plan) {
        StructureTemplateManager templates = context.structureTemplateManager();
        Rotation rotation = plan.rotation();
        BlockPos origin = plan.origin();
        builder.addPiece(new ShrinePiece(templates, pieces.hall(), origin, rotation));
        for (int step = 0; step < plan.closed().size(); step++) {
            BlockPos local = layout.entrance().offset(0, step, step).subtract(layout.stair());
            ResourceLocation template = plan.closed().get(step) ? pieces.step() : pieces.openStep();
            builder.addPiece(new ShrinePiece(templates, template, place(origin, local, rotation), rotation));
        }
        BlockPos shaft = place(origin, layout.shaft(), rotation);
        if (plan.light().wet()) {
            builder.addPiece(new ShrinePiece(templates, pieces.seal(), shaft.below(), rotation));
        } else {
            int top = plan.light().floor() - 1;
            for (int y = shaft.getY(); y < top; y++) {
                builder.addPiece(new ShrinePiece(templates, pieces.shaft(), shaft.atY(y), rotation));
            }
            builder.addPiece(new ShrinePiece(templates, pieces.cap(), shaft.atY(top), rotation));
        }
        builder.addPiece(new ShrinePiece(templates, pieces.marker(), plan.mouth().subtract(turn(layout.mouth(), rotation)), rotation));
    }

    private static BlockPos turn(BlockPos local, Rotation rotation) {
        return StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO);
    }

    private static BlockPos place(BlockPos origin, BlockPos local, Rotation rotation) {
        return origin.offset(turn(local, rotation));
    }

    private static Column column(GenerationContext context, BlockPos pos) {
        LevelHeightAccessor height = context.heightAccessor();
        NoiseColumn column = context.chunkGenerator().getBaseColumn(pos.getX(), pos.getZ(), height, context.randomState());
        boolean wet = false;
        for (int y = height.getMaxBuildHeight() - 1; y >= height.getMinBuildHeight(); y--) {
            BlockState state = column.getBlock(y);
            if (!state.getFluidState().isEmpty()) {
                wet = true;
            } else if (!state.isAir()) {
                return new Column(y + 1, wet);
            }
        }
        return new Column(height.getMinBuildHeight(), wet);
    }
}
