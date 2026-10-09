package io.github.roman16.wayfarer;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Notices {
    private Notices() {}

    public record Given(List<ItemStack> stacks) implements CustomPacketPayload {
        public static final Type<Given> TYPE = new Type<>(Wayfarer.id("given"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Given> STREAM_CODEC = ItemStack.LIST_STREAM_CODEC.map(Given::new, Given::stacks);

        @Override
        public Type<Given> type() {
            return TYPE;
        }
    }

    public record Written(ItemStack icon, Component text) implements CustomPacketPayload {
        public static final Type<Written> TYPE = new Type<>(Wayfarer.id("written"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Written> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Written::icon, ComponentSerialization.STREAM_CODEC, Written::text, Written::new);

        @Override
        public Type<Written> type() {
            return TYPE;
        }
    }

    public static void given(ServerPlayer player, List<ItemStack> stacks) {
        List<ItemStack> shown = stacks.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        if (!shown.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new Given(shown));
        }
    }

    public static void written(MinecraftServer server, Component text) {
        server.getPlayerList().getPlayers().forEach(player -> written(player, new ItemStack(Items.WRITABLE_BOOK), text));
    }

    public static void written(ServerPlayer player, ItemStack icon, Component text) {
        PacketDistributor.sendToPlayer(player, new Written(icon, text));
    }
}
