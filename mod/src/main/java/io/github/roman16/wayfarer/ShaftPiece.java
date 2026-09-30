package io.github.roman16.wayfarer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class ShaftPiece extends StructurePiece {
    public static final int LIGHT_EVERY = 6;

    private static final int AGE_SPAN = 8;
    private static final double PATCH = 4.0;
    private static final double GRAIN = 1.6;
    private static final double SPREAD = 1.6;
    private static final int DOOR_HEIGHT = 3;
    private static final int HEADROOM = 4;
    private static final int SUNKEN = 2;
    private static final int LANDING = 2;
    private static final String DESIGN = "design";
    private static final String EXIT = "exit";
    private static final String FLOOR = "floor";
    private static final String ORIGIN = "origin";
    private static final String ROTATION = "rotation";
    private static final String TOP = "top";

    public enum Style implements StringRepresentable {
        LADDER("ladder", 5, 1, List.of(Direction.SOUTH)),
        SPIRAL("spiral", 11, 12, List.of(Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.NORTH)),
        SWITCHBACK("switchback", 11, 10, List.of(Direction.SOUTH, Direction.NORTH));

        public static final Codec<Style> CODEC = StringRepresentable.fromEnum(Style::values);

        private final String id;
        public final int size;
        private final int rise;
        public final List<Direction> exits;

        Style(String id, int size, int rise, List<Direction> exits) {
            this.id = id;
            this.size = size;
            this.rise = rise;
            this.exits = exits;
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        public int depth(int atLeast, Direction exit) {
            int depth = Math.max(atLeast, 1);
            return depth + Math.floorMod(head(exit) + SUNKEN - depth, rise);
        }

        private int head(Direction exit) {
            return switch (this) {
                case LADDER -> 0;
                case SPIRAL -> switch (exit) {
                    case SOUTH -> 2;
                    case WEST -> 5;
                    case NORTH -> 8;
                    default -> 11;
                };
                case SWITCHBACK -> exit == Direction.SOUTH ? 4 : 9;
            };
        }
    }

    public record Design(Style style, List<BlockState> landing, BlockState light, List<BlockState> pillar, BlockState stairs,
            List<BlockState> wall) {
        public static final Codec<Design> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Style.CODEC.fieldOf("style").forGetter(Design::style),
                        BlockState.CODEC.listOf().fieldOf("landing").forGetter(Design::landing),
                        BlockState.CODEC.fieldOf("light").forGetter(Design::light),
                        BlockState.CODEC.listOf().fieldOf("pillar").forGetter(Design::pillar),
                        BlockState.CODEC.fieldOf("stairs").forGetter(Design::stairs),
                        BlockState.CODEC.listOf().fieldOf("wall").forGetter(Design::wall))
                .apply(instance, Design::new));

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("style", style.getSerializedName());
            tag.put("landing", states(landing));
            tag.put("light", NbtUtils.writeBlockState(light));
            tag.put("pillar", states(pillar));
            tag.put("stairs", NbtUtils.writeBlockState(stairs));
            tag.put("wall", states(wall));
            return tag;
        }

        static Design load(HolderGetter<Block> blocks, CompoundTag tag) {
            return new Design(
                    Style.valueOf(tag.getString("style").toUpperCase(java.util.Locale.ROOT)),
                    states(blocks, tag.getList("landing", Tag.TAG_COMPOUND)),
                    NbtUtils.readBlockState(blocks, tag.getCompound("light")),
                    states(blocks, tag.getList("pillar", Tag.TAG_COMPOUND)),
                    NbtUtils.readBlockState(blocks, tag.getCompound("stairs")),
                    states(blocks, tag.getList("wall", Tag.TAG_COMPOUND)));
        }

        private static ListTag states(List<BlockState> states) {
            ListTag list = new ListTag();
            states.forEach(state -> list.add(NbtUtils.writeBlockState(state)));
            return list;
        }

        private static List<BlockState> states(HolderGetter<Block> blocks, ListTag list) {
            return list.stream().map(entry -> NbtUtils.readBlockState(blocks, (CompoundTag) entry)).toList();
        }
    }

    private record Step(int offset, Direction facing) {}

    private final BlockPos origin;
    private final Rotation rotation;
    private final int floor;
    private final int top;
    private final Direction exit;
    private final Design design;

    public ShaftPiece(BlockPos origin, Rotation rotation, int floor, int top, Direction exit, Design design) {
        super(Wayfarer.SHAFT_PIECE.get(), 0, box(origin, rotation, floor, top, design.style().size));
        this.origin = origin;
        this.rotation = rotation;
        this.floor = floor;
        this.top = top;
        this.exit = exit;
        this.design = design;
    }

    public ShaftPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(Wayfarer.SHAFT_PIECE.get(), tag);
        this.origin = NbtUtils.readBlockPos(tag, ORIGIN).orElseThrow();
        this.rotation = Rotation.valueOf(tag.getString(ROTATION));
        this.floor = tag.getInt(FLOOR);
        this.top = tag.getInt(TOP);
        this.exit = Direction.byName(tag.getString(EXIT));
        this.design = Design.load(context.registryAccess().lookupOrThrow(Registries.BLOCK), tag.getCompound(DESIGN));
    }

    private static BoundingBox box(BlockPos origin, Rotation rotation, int floor, int top, int size) {
        return BoundingBox.fromCorners(place(origin, rotation, 0, floor - 2, 0), place(origin, rotation, size - 1, top - 1, size - 1));
    }

    private static BlockPos place(BlockPos origin, Rotation rotation, int x, int y, int z) {
        return origin.offset(StructureTemplate.transform(new BlockPos(x, 0, z), Mirror.NONE, rotation, BlockPos.ZERO)).atY(y);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.put(ORIGIN, NbtUtils.writeBlockPos(origin));
        tag.putString(ROTATION, rotation.name());
        tag.putInt(FLOOR, floor);
        tag.putInt(TOP, top);
        tag.putString(EXIT, exit.getSerializedName());
        tag.put(DESIGN, design.save());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
            BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
        int size = design.style().size;
        for (int y = floor - 2; y < top; y++) {
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    BlockPos pos = place(origin, rotation, x, y, z);
                    if (chunk.isInside(pos)) {
                        level.setBlock(pos, block(x, y, z, pos).rotate(level, pos, rotation), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private BlockState block(int x, int y, int z, BlockPos world) {
        int last = design.style().size - 1;
        int middle = last / 2;
        if (y == floor - 2) {
            return pick(design.wall(), world);
        }
        if (x == 0 || x == last || z == 0 || z == last) {
            boolean doorway = z == 0 && Math.abs(x - middle) <= 1 && y >= floor && y < floor + DOOR_HEIGHT;
            if (doorway) {
                return Blocks.AIR.defaultBlockState();
            }
            if (y == top - 1 && design.style() != Style.LADDER && threshold(x, z)) {
                return design.stairs().trySetValue(StairBlock.FACING, exit);
            }
            boolean lamp = (x == middle || z == middle) && lit(y);
            return lamp ? design.light() : pick(design.wall(), world);
        }
        if (y == floor - 1) {
            return pick(design.landing(), world);
        }
        return switch (design.style()) {
            case LADDER -> ladder(x, y, z);
            case SPIRAL -> spiral(x, y, z, world);
            case SWITCHBACK -> switchback(x, y, z, world);
        };
    }

    private boolean threshold(int x, int z) {
        int last = design.style().size - 1;
        int middle = last / 2;
        return switch (exit) {
            case NORTH -> x == middle && z == 0;
            case EAST -> x == last && z == middle;
            case WEST -> x == 0 && z == middle;
            default -> x == middle && z == last;
        };
    }

    private boolean lit(int y) {
        return Math.floorMod(y - floor, LIGHT_EVERY) == LIGHT_EVERY / 2 && y < top - 2;
    }

    private BlockState ladder(int x, int y, int z) {
        if (x == 2 && z == 3 && y < top - 1) {
            return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
        }
        return Blocks.AIR.defaultBlockState();
    }

    private BlockState spiral(int x, int y, int z, BlockPos world) {
        int middle = design.style().size / 2;
        if (Math.abs(x - middle) <= 1 && Math.abs(z - middle) <= 1 && y <= top - SUNKEN) {
            boolean face = (x == middle) != (z == middle);
            return face && lit(y) ? design.light() : pick(design.pillar(), world);
        }
        return flight(spiralStep(x, z, middle), z < middle - 1 && x >= middle - 1, y, world);
    }

    private BlockState switchback(int x, int y, int z, BlockPos world) {
        int middle = design.style().size / 2;
        int last = design.style().size - 1;
        if (x == middle && z > LANDING && z < last - LANDING && y <= top - SUNKEN) {
            return z == middle && lit(y) ? design.light() : pick(design.pillar(), world);
        }
        return flight(switchbackStep(x, z, middle, last), z <= LANDING && x >= middle - 1, y, world);
    }

    private BlockState flight(Step step, boolean passage, int y, BlockPos world) {
        int rise = design.style().rise;
        int head = top - SUNKEN;
        if (y > head) {
            return Blocks.AIR.defaultBlockState();
        }
        if (y == head) {
            int behind = Math.floorMod(Math.floorMod(head - floor, rise) - step.offset(), rise);
            if (behind >= 1 && behind <= HEADROOM) {
                return Blocks.AIR.defaultBlockState();
            }
            if (behind != 0) {
                return pick(design.landing(), world);
            }
        }
        int above = y - floor - step.offset();
        if (Math.floorMod(above, rise) == 0) {
            return step.facing() == null
                    ? pick(design.landing(), world)
                    : design.stairs().trySetValue(StairBlock.FACING, step.facing());
        }
        return above < 0 && !passage ? pick(design.pillar(), world) : Blocks.AIR.defaultBlockState();
    }

    private static Step spiralStep(int x, int z, int middle) {
        int low = middle - 1;
        int high = middle + 1;
        boolean west = x < low;
        boolean east = x > high;
        boolean north = z < low;
        boolean south = z > high;
        if (east && south) {
            return new Step(2, null);
        }
        if (west && south) {
            return new Step(5, null);
        }
        if (west && north) {
            return new Step(8, null);
        }
        if (east && north) {
            return new Step(11, null);
        }
        if (east) {
            return new Step(z - low, Direction.SOUTH);
        }
        if (south) {
            return new Step(3 + (high - x), Direction.WEST);
        }
        if (west) {
            return new Step(6 + (high - z), Direction.NORTH);
        }
        return new Step(9 + (x - low), Direction.EAST);
    }

    private static Step switchbackStep(int x, int z, int middle, int last) {
        int first = LANDING + 1;
        int end = last - LANDING - 1;
        if (z > end) {
            return new Step(end - first, null);
        }
        if (z < first) {
            return new Step(2 * (end - first) + 1, null);
        }
        return x > middle ? new Step(z - first, Direction.SOUTH) : new Step(end - first + 1 + (end - z), Direction.NORTH);
    }

    private BlockState pick(List<BlockState> ordered, BlockPos pos) {
        double age = Mth.clamp(1 - (pos.getY() - floor) / (double) AGE_SPAN, 0, 1);
        double value = 0.5 + (patch(pos, PATCH) * 0.65 + patch(pos, GRAIN) * 0.35 - 0.5) * SPREAD + (age - 0.35) * 0.45;
        return ordered.get((int) (Mth.clamp(value, 0, 0.999) * ordered.size()));
    }

    private static double patch(BlockPos pos, double scale) {
        double fx = pos.getX() / scale;
        double fy = pos.getY() / scale;
        double fz = pos.getZ() / scale;
        int x0 = Mth.floor(fx);
        int y0 = Mth.floor(fy);
        int z0 = Mth.floor(fz);
        double tx = ease(fx - x0);
        double ty = ease(fy - y0);
        double tz = ease(fz - z0);
        double total = 0;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    double weight = (dx == 1 ? tx : 1 - tx) * (dy == 1 ? ty : 1 - ty) * (dz == 1 ? tz : 1 - tz);
                    total += weight * lattice(x0 + dx, y0 + dy, z0 + dz, scale);
                }
            }
        }
        return total;
    }

    private static double lattice(int x, int y, int z, double scale) {
        long seed = (x * 3129871L) ^ (z * 116129781L) ^ y ^ Double.doubleToLongBits(scale);
        seed = seed * seed * 42317861L + seed * 11L;
        seed = seed * 0x5DEECE66DL + 0xBL;
        return ((seed >>> 17) & 0xFFFFFF) / (double) 0x1000000;
    }

    private static double ease(double t) {
        return t * t * (3 - 2 * t);
    }
}
