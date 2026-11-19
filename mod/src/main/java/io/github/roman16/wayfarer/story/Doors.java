package io.github.roman16.wayfarer.story;

import com.google.gson.JsonElement;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Shards;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

public final class Doors {
    private static final String QUEST = "the_doors";
    private static final String STEP = "the_doors/the_doors";
    private static final String BLIND = "blind";
    private static final String GATE = "the_gate";
    private static final String BAND = "the_band";
    private static final String DROPS = "drops";
    private static final String SPARED = "the_worm/the_worm=spared";
    private static final String TIDE_FOLK = "tide_folk";
    private static final String LAID = "heirs_shards";
    private static final String MARK = Wayfarer.MODID + "_heirs_shard";
    private static final double BEFORE = 3;
    private static final double RISE = 1.5;
    private static final double APART = 0.6;

    private Doors() {}

    public static boolean blinding(MinecraftServer server) {
        return Engine.running(server) && BLIND.equals(Progress.of(server).choices.get(STEP)) && Progress.of(server).open(QUEST);
    }

    static int trusting(Progress progress) {
        return (progress.trust(TIDE_FOLK) > 0 ? 1 : 0) + (Engine.chose(progress, SPARED) ? 1 : 0);
    }

    static List<String> heirs() {
        return Story.get().quest(GATE).flatMap(quest -> quest.steps().stream().filter(step -> step.id().equals(BAND)).findFirst())
                .map(step -> step.spec().getAsJsonArray(DROPS).asList().stream().map(JsonElement::getAsString).toList()).orElse(List.of());
    }

    static boolean dropped(Progress progress) {
        return progress.past(GATE, BAND);
    }

    public static List<ResourceLocation> frame(MinecraftServer server) {
        List<ResourceLocation> shards = new ArrayList<>(Journey.claimed(server));
        if (Engine.running(server) && dropped(Progress.of(server))) {
            heirs().stream().map(Shards::id).filter(shard -> !shards.contains(shard)).forEach(shards::add);
        }
        return shards;
    }

    public static int road() {
        return Shards.TWELVE - heirs().size();
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        if (progress.done(QUEST) || blinding(server)) {
            if (progress.place(LAID).isPresent()) {
                gather(level);
                progress.places.remove(LAID);
                progress.setDirty();
            }
            return;
        }
        if (!dropped(progress) || progress.place(LAID).isPresent()) {
            return;
        }
        progress.place(Act3.KEEPERS_FRAME).filter(level::isLoaded).ifPresent(frame -> lay(level, progress, frame));
    }

    private static void lay(ServerLevel level, Progress progress, BlockPos frame) {
        Vec3 centre = Act3.centre(level, frame);
        Vec3 toward = Vec3.atBottomCenterOf(progress.place(Act3.KEEPERS).orElse(frame)).subtract(centre);
        Direction facing = Direction.getNearest(toward.x, 0, toward.z);
        for (Direction side : List.of(facing, facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite())) {
            Optional<Vec3> spot = floor(level, centre, side);
            if (spot.isPresent()) {
                Vec3 across = Vec3.atLowerCornerOf(side.getClockWise().getNormal());
                List<String> eyes = heirs();
                for (int index = 0; index < eyes.size(); index++) {
                    Visit.lay(level, spot.get().add(across.scale((index - (eyes.size() - 1) / 2.0) * APART)), Shards.id(eyes.get(index)), MARK);
                }
                progress.place(LAID, frame);
                progress.setDirty();
                return;
            }
        }
    }

    private static Optional<Vec3> floor(ServerLevel level, Vec3 centre, Direction side) {
        return Ground.feet(level, centre.add(Vec3.atLowerCornerOf(side.getNormal()).scale(BEFORE))).filter(feet -> Math.abs(feet.y - centre.y) <= RISE);
    }

    public static void gather(ServerLevel level) {
        level.getEntities(EntityTypeTest.forClass(Display.ItemDisplay.class), display -> display.getTags().contains(MARK)).forEach(Entity::discard);
    }
}
