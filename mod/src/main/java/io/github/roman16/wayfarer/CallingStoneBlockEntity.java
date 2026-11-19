package io.github.roman16.wayfarer;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CallingStoneBlockEntity extends BlockEntity {
    private static final String SHARD = "shard";

    private ResourceLocation shard;

    public CallingStoneBlockEntity(BlockPos pos, BlockState state) {
        super(Wayfarer.CALLING_STONE_ENTITY.get(), pos, state);
    }

    public Optional<ResourceLocation> shard() {
        return Optional.ofNullable(shard);
    }

    public void hold(Optional<ResourceLocation> held) {
        shard = held.orElse(null);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        shard = tag.contains(SHARD) ? ResourceLocation.parse(tag.getString(SHARD)) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (shard != null) {
            tag.putString(SHARD, shard.toString());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
