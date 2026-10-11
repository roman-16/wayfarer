package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

public final class Marks {
    static final String MARK = "mark_";
    private static final String GIFT = "far_sight";
    private static final String SCRATCH = "marks";
    private static final String NEXT = "next";

    private Marks() {}

    public record Mark(BlockPos at) implements CustomPacketPayload {
        public static final Type<Mark> TYPE = new Type<>(Wayfarer.id("mark"));
        public static final StreamCodec<ByteBuf, Mark> STREAM_CODEC = BlockPos.STREAM_CODEC.map(Mark::new, Mark::at);

        @Override
        public Type<Mark> type() {
            return TYPE;
        }
    }

    public static void mark(ServerPlayer player, BlockPos at) {
        Progress progress = Progress.of(player.server);
        int reach = Balance.get().farSight().markBlocks();
        if (!Engine.running(player.server) || !progress.gift(GIFT) || !player.isScoping() || player.level().dimension() != Level.OVERWORLD
                || player.blockPosition().distSqr(at) > (double) reach * reach) {
            return;
        }
        CompoundTag scratch = progress.scratch(SCRATCH);
        int next = scratch.getInt(NEXT);
        progress.place(MARK + next, at);
        scratch.putInt(NEXT, (next + 1) % Balance.get().farSight().marks());
        progress.setDirty();
        Talk.tell(player, Component.translatable("wayfarer.far_sight.marked", (int) Math.sqrt(player.blockPosition().distSqr(at))));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1, 1);
        Journal.syncAll(player.server);
    }

    static int count() {
        return Balance.get().farSight().marks();
    }
}
