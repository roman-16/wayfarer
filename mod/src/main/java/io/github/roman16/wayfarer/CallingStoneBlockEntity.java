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
    private static final String HALL = "hall";

    private ResourceLocation hall;

    public CallingStoneBlockEntity(BlockPos pos, BlockState state) {
        super(Wayfarer.CALLING_STONE_ENTITY.get(), pos, state);
    }

    public Optional<ResourceLocation> hall() {
        return Optional.ofNullable(hall);
    }

    public void hold(Optional<ResourceLocation> held) {
        hall = held.orElse(null);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hall = tag.contains(HALL) ? ResourceLocation.parse(tag.getString(HALL)) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (hall != null) {
            tag.putString(HALL, hall.toString());
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
