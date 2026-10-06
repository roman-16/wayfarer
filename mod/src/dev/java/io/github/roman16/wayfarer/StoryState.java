package io.github.roman16.wayfarer;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class StoryState {
    private static final int OWNER = 4;
    private static final String PREFIX = Wayfarer.MODID + "_";
    private static final String SUFFIX = ".dat";

    private StoryState() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("state")
                        .requires(source -> source.hasPermission(OWNER))
                        .executes(context -> report(context.getSource()))));
    }

    private static int report(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        JsonObject state = new JsonObject();
        Path written = server.getServerDirectory().resolve("driver").resolve("state.json").toAbsolutePath().normalize();
        try {
            server.getAllLevels().forEach(level -> level.getDataStorage().save());
            IOUtilities.waitUntilIOWorkerComplete();
            for (ServerLevel level : server.getAllLevels()) {
                Path folder = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT)).resolve("data");
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                List<Path> files;
                try (Stream<Path> listing = Files.list(folder)) {
                    files = listing.filter(file -> file.getFileName().toString().startsWith(PREFIX))
                            .filter(file -> file.getFileName().toString().endsWith(SUFFIX))
                            .sorted()
                            .toList();
                }
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    String key = name.substring(PREFIX.length(), name.length() - SUFFIX.length());
                    CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                    String place = level.dimension() == Level.OVERWORLD ? key : level.dimension().location() + "/" + key;
                    state.add(place, NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, saved.getCompound("data")));
                }
            }
            Files.createDirectories(written.getParent());
            Files.writeString(written, state.toString());
        } catch (IOException exception) {
            source.sendFailure(Component.literal("state failed: could not read the world's saved data or write it to " + written + ": " + exception.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("state: " + written), false);
        return 1;
    }
}
