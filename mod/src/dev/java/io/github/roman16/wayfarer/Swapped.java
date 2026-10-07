package io.github.roman16.wayfarer;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.EventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class Swapped {
    private static final int OWNER = 4;
    private static final Field LISTENERS = listeners();

    private Swapped() {}

    public static String listen(String name) {
        Class<?> type;
        try {
            type = Class.forName(name, false, Swapped.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            return "listeners: there is no class " + name;
        }
        if (!registered(type)) {
            return "listeners: " + name + " listens on no bus the game posts to; it needs a restart";
        }
        NeoForge.EVENT_BUS.unregister(type);
        NeoForge.EVENT_BUS.register(type);
        return "listeners: " + name + " is registered again";
    }

    private static boolean registered(Class<?> type) {
        try {
            return ((Map<?, ?>) LISTENERS.get(NeoForge.EVENT_BUS)).containsKey(type);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Field listeners() {
        try {
            Field field = EventBus.class.getDeclaredField("listeners");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("listeners")
                        .requires(source -> source.hasPermission(OWNER))
                        .then(Commands.argument("classes", StringArgumentType.greedyString()).executes(context -> {
                            for (String name : StringArgumentType.getString(context, "classes").split(" ")) {
                                String said = listen(name);
                                context.getSource().sendSuccess(() -> Component.literal(said), false);
                            }
                            return 1;
                        }))));
    }
}
