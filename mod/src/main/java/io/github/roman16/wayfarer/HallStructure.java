package io.github.roman16.wayfarer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public final class HallStructure extends Structure {
    public record Oculus(BlockPos at, ResourceLocation cap, ResourceLocation column, ResourceLocation seal) {
        static final Codec<Oculus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        BlockPos.CODEC.fieldOf("at").forGetter(Oculus::at),
                        ResourceLocation.CODEC.fieldOf("cap").forGetter(Oculus::cap),
                        ResourceLocation.CODEC.fieldOf("column").forGetter(Oculus::column),
                        ResourceLocation.CODEC.fieldOf("seal").forGetter(Oculus::seal))
                .apply(instance, Oculus::new));
    }

    public record Layout(int cover, BlockPos door, Optional<Oculus> oculus) {
        static final Codec<Layout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.INT.fieldOf("cover").forGetter(Layout::cover),
                        BlockPos.CODEC.fieldOf("door").forGetter(Layout::door),
                        Oculus.CODEC.optionalFieldOf("oculus").forGetter(Layout::oculus))
                .apply(instance, Layout::new));
    }

    public record Pieces(ResourceLocation entrance, ResourceLocation hall) {
        static final Codec<Pieces> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("entrance").forGetter(Pieces::entrance),
                        ResourceLocation.CODEC.fieldOf("hall").forGetter(Pieces::hall))
                .apply(instance, Pieces::new));
    }

    public static final MapCodec<HallStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    settingsCodec(instance),
                    Layout.CODEC.fieldOf("layout").forGetter(HallStructure::layout),
                    Pieces.CODEC.fieldOf("pieces").forGetter(HallStructure::pieces),
                    ShaftPiece.Design.CODEC.fieldOf("shaft").forGetter(HallStructure::shaft))
            .apply(instance, HallStructure::new));

    private static final int MIN_DEPTH = 6;

    private record Column(int floor, boolean wet) {}

    private record Plan(Rotation rotation, BlockPos hall, BlockPos shaft, int floor, int top, Direction exit, Optional<Column> light) {}

    private final Layout layout;
    private final Pieces pieces;
    private final ShaftPiece.Design shaft;

    public HallStructure(StructureSettings settings, Layout layout, Pieces pieces, ShaftPiece.Design shaft) {
        super(settings);
        this.layout = layout;
        this.pieces = pieces;
        this.shaft = shaft;
    }

    public Layout layout() {
        return layout;
    }

    public Pieces pieces() {
        return pieces;
    }

    public ShaftPiece.Design shaft() {
        return shaft;
    }

    @Override
    public StructureType<?> type() {
        return Wayfarer.HALL.get();
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
        return plan(context, center).map(plan -> new GenerationStub(surface, builder -> build(builder, context, plan)));
    }

    private Optional<Plan> plan(GenerationContext context, BlockPos center) {
        Vec3i hall = context.structureTemplateManager().getOrCreate(pieces.hall()).getSize();
        Plan best = null;
        for (Rotation rotation : Rotation.values()) {
            Plan plan = plan(context, rotation, hall, center);
            if (plan != null && (best == null || plan.top() - plan.floor() < best.top() - best.floor())) {
                best = plan;
            }
        }
        return Optional.ofNullable(best);
    }

    private Plan plan(GenerationContext context, Rotation rotation, Vec3i hall, BlockPos center) {
        BlockPos corner = center.subtract(turn(new BlockPos(hall.getX() / 2, 0, hall.getZ() / 2), rotation));
        int size = this.shaft.style().size;
        BlockPos shaftLocal = new BlockPos(layout.door().getX() - size / 2, 0, layout.door().getZ() + 1);
        BlockPos shaft = place(corner, shaftLocal, rotation);
        if (column(context, place(shaft, new BlockPos(size / 2, 0, size / 2), rotation)).wet()) {
            return null;
        }
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
        int deepestFloor = lowest - hall.getY() - layout.cover() + layout.door().getY();
        Plan best = null;
        for (Direction exit : this.shaft.style().exits) {
            Column outside = column(context, place(shaft, outside(exit, size), rotation));
            if (outside.wet()) {
                continue;
            }
            int top = outside.floor();
            int depth = this.shaft.style().depth(Math.max(MIN_DEPTH, top - deepestFloor), exit);
            int floor = top - depth;
            if (floor - layout.door().getY() <= context.heightAccessor().getMinBuildHeight() + 1) {
                continue;
            }
            if (best == null || depth < best.top() - best.floor()) {
                Optional<Column> light = layout.oculus().map(oculus -> column(context, place(corner, oculus.at(), rotation)));
                best = new Plan(rotation, corner.atY(floor - layout.door().getY()), shaft, floor, top, exit, light);
            }
        }
        return best;
    }

    private static BlockPos outside(Direction exit, int size) {
        int middle = size / 2;
        return switch (exit) {
            case SOUTH -> new BlockPos(middle, 0, size);
            case NORTH -> new BlockPos(middle, 0, -1);
            case EAST -> new BlockPos(size, 0, middle);
            default -> new BlockPos(-1, 0, middle);
        };
    }

    private void build(StructurePiecesBuilder builder, GenerationContext context, Plan plan) {
        StructureTemplateManager templates = context.structureTemplateManager();
        Rotation rotation = plan.rotation();
        builder.addPiece(new HallPiece(templates, pieces.hall(), plan.hall(), rotation));
        builder.addPiece(new ShaftPiece(plan.shaft(), rotation, plan.floor(), plan.top(), plan.exit(), shaft));
        Rotation facing = rotation.getRotated(turning(plan.exit()));
        int width = templates.getOrCreate(pieces.entrance()).getSize().getX();
        int size = shaft.style().size;
        int inset = (width - size) / 2;
        BlockPos entrance = aligned(plan.shaft().atY(plan.top() - 1), rotation, facing, width, inset, size);
        builder.addPiece(new EntrancePiece(templates, pieces.entrance(), entrance, facing, inset));
        plan.light().ifPresent(light -> {
            Oculus oculus = layout.oculus().orElseThrow();
            BlockPos at = place(plan.hall(), oculus.at(), rotation);
            if (light.wet()) {
                builder.addPiece(new HallPiece(templates, oculus.seal(), at.below(), rotation));
                return;
            }
            int top = light.floor() - 1;
            for (int y = at.getY(); y < top; y++) {
                builder.addPiece(new HallPiece(templates, oculus.column(), at.atY(y), rotation));
            }
            builder.addPiece(new HallPiece(templates, oculus.cap(), at.atY(top), rotation));
        });
    }

    private static Rotation turning(Direction exit) {
        return switch (exit) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static BlockPos aligned(BlockPos shaft, Rotation shaftRotation, Rotation entranceRotation, int width, int inset, int size) {
        int last = size - 1;
        BlockPos far = place(shaft, new BlockPos(last, 0, last), shaftRotation);
        BlockPos min = new BlockPos(Math.min(shaft.getX(), far.getX()) - inset, shaft.getY(), Math.min(shaft.getZ(), far.getZ()) - inset);
        BlockPos spread = turn(new BlockPos(width - 1, 0, width - 1), entranceRotation);
        return min.offset(-Math.min(0, spread.getX()), 0, -Math.min(0, spread.getZ()));
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
