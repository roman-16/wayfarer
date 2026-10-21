package io.github.roman16.wayfarer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.client.gui.CuriosScreen;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;
import top.theillusivec4.curios.common.network.client.CPacketPage;

final class Hands {
    private static final int HOTBAR = 9;
    private static final int INVENTORY = 36;
    private static final int FETCHES = 4;
    private static final int OFF_HAND = 40;
    private static final int ARMOUR_TOP = 8;
    private static final int TABLE_BLOCKS = 5;
    private static final double HALF = 0.5;
    private static final double BEYOND = 0.1;
    private static final String JEI = "jei";
    private static final String CURIOS_SLOT = "curios.identifier.";

    private Hands() {}

    static Optional<Item> item(String name) {
        return byId(name).or(() -> BuiltInRegistries.ITEM.stream().filter(item -> item.getDescription().getString().equalsIgnoreCase(name.strip())).findFirst());
    }

    private static Optional<Item> byId(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name.strip().toLowerCase(Locale.ROOT));
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? Optional.of(BuiltInRegistries.ITEM.get(id)) : Optional.empty();
    }

    static int slot(LocalPlayer player, String name) {
        Optional<Item> named = byId(name);
        Inventory inventory = player.getInventory();
        return IntStream.concat(IntStream.range(0, INVENTORY), IntStream.of(OFF_HAND))
                .filter(index -> {
                    ItemStack stack = inventory.getItem(index);
                    return !stack.isEmpty() && (stack.getHoverName().getString().equalsIgnoreCase(name.strip()) || named.map(stack::is).orElse(false));
                })
                .findFirst()
                .orElse(-1);
    }

    static String held(ItemStack stack) {
        return stack.getCount() > 1 ? stack.getCount() + " " + stack.getHoverName().getString() : stack.getHoverName().getString();
    }

    static boolean same(BlockState state, Block block) {
        return state.is(block) || block.asItem() != Items.AIR && state.getBlock().asItem() == block.asItem();
    }

    static List<Block> blocks(Item item) {
        return BuiltInRegistries.BLOCK.stream().filter(block -> block.asItem() == item).toList();
    }

    static Optional<Entity> creature(Minecraft minecraft, String type, double within) {
        ResourceLocation id = ResourceLocation.tryParse(type.strip());
        LocalPlayer player = minecraft.player;
        return StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity != player && entity.distanceTo(player) <= within
                        && (entity.getDisplayName().getString().equalsIgnoreCase(type.strip())
                        || id != null && (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(id) || picked(entity).equals(id))))
                .min(Comparator.comparingDouble(player::distanceTo));
    }

    private static ResourceLocation picked(Entity entity) {
        ItemStack pick = entity.getPickResult();
        return pick == null || pick.isEmpty() ? BuiltInRegistries.ITEM.getDefaultKey() : BuiltInRegistries.ITEM.getKey(pick.getItem());
    }

    static String near(Minecraft minecraft, double within) {
        LocalPlayer player = minecraft.player;
        String near = StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity != player && entity.distanceTo(player) <= within && (entity instanceof LivingEntity || !picked(entity).equals(
                        BuiltInRegistries.ITEM.getDefaultKey())))
                .sorted(Comparator.comparingDouble(player::distanceTo))
                .limit(HOTBAR)
                .map(entity -> Senses.describe(entity) + " (" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + "), " + Math.round(entity.distanceTo(player))
                        + " blocks")
                .collect(Collectors.joining("; "));
        return near.isEmpty() ? "" : "; near: " + near;
    }

    static void hotbar(Minecraft minecraft, String argument) {
        String[] words = argument.strip().split(" ");
        String last = words[words.length - 1];
        boolean numbered = words.length > 1 && last.matches("[1-9]");
        String name = numbered ? argument.strip().substring(0, argument.strip().lastIndexOf(' ')).strip() : argument.strip();
        LocalPlayer player = minecraft.player;
        Inventory inventory = player.getInventory();
        int to = numbered ? Integer.parseInt(last) - 1 : IntStream.range(0, HOTBAR).filter(index -> inventory.getItem(index).isEmpty()).findFirst().orElse(-1);
        Optional<Slot> stored = minecraft.screen instanceof AbstractContainerScreen<?> ? stored(player, name) : Optional.empty();
        if (stored.isPresent()) {
            if (to < 0) {
                Driver.fail("hotbar failed: the hotbar is full; name a slot, such as hotbar " + name + " 9");
                return;
            }
            String taken = held(stored.get().getItem());
            AbstractContainerMenu menu = player.containerMenu;
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, stored.get().index, to, ClickType.SWAP, player);
            settled(minecraft, () -> Driver.say("hotbar: " + taken + " from the " + minecraft.screen.getTitle().getString() + " in slot " + (to + 1)));
            return;
        }
        int from = slot(player, name);
        if (from < 0) {
            Driver.fail("hotbar failed: no " + name + " in the inventory");
            return;
        }
        if (from < HOTBAR && (!numbered || to == from)) {
            Driver.say("hotbar: " + held(inventory.getItem(from)) + " in slot " + (from + 1) + " already");
            return;
        }
        if (to < 0) {
            Driver.fail("hotbar failed: the hotbar is full; name a slot, such as hotbar " + name + " 9");
            return;
        }
        String moved = held(inventory.getItem(from));
        open(minecraft, () -> {
            AbstractContainerMenu menu = player.containerMenu;
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot(menu, inventory, from), to, ClickType.SWAP, player);
            settled(minecraft, () -> {
                close(minecraft);
                Driver.say("hotbar: " + moved + " in slot " + (to + 1));
            });
        });
    }

    private static int free(Inventory inventory) {
        for (int index = 0; index < HOTBAR; index++) {
            if (inventory.getItem(index).isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private static Optional<Slot> stored(LocalPlayer player, String name) {
        Optional<Item> named = byId(name);
        return player.containerMenu.slots.stream()
                .filter(slot -> slot.container != player.getInventory() && slot.hasItem() && (slot.getItem().getHoverName().getString().equalsIgnoreCase(name.strip())
                        || named.map(slot.getItem()::is).orElse(false)))
                .max(Comparator.comparingInt(slot -> uses(slot.getItem())));
    }

    private static int uses(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : stack.getCount();
    }

    static void wear(Minecraft minecraft, String name) {
        LocalPlayer player = minecraft.player;
        Inventory inventory = player.getInventory();
        int from = slot(player, name.strip());
        if (from < 0) {
            Driver.fail("wear failed: no " + name.strip() + " in the inventory");
            return;
        }
        ItemStack stack = inventory.getItem(from);
        String worn = stack.getHoverName().getString();
        if (!CuriosApi.getItemStackSlots(stack, player).isEmpty()) {
            PacketDistributor.sendToServer(new CPacketOpenCurios(ItemStack.EMPTY));
            Driver.after(() -> player.containerMenu instanceof CuriosContainer && minecraft.screen instanceof CuriosScreen, "the charms to open",
                    () -> Driver.drawn("the charms", () -> charm(minecraft, from, worn)));
            return;
        }
        EquipmentSlot slot = player.getEquipmentSlotForItem(stack);
        if (!slot.isArmor()) {
            Driver.fail("wear failed: " + worn + " is nothing to wear");
            return;
        }
        open(minecraft, () -> {
            AbstractContainerMenu menu = player.containerMenu;
            swap(minecraft, menu, menuSlot(menu, inventory, from), ARMOUR_TOP - slot.getIndex());
            settled(minecraft, () -> {
                boolean on = player.getItemBySlot(slot).getHoverName().getString().equals(worn);
                close(minecraft);
                if (on) {
                    Driver.say("wear: " + worn + " on, in the " + slot.getName() + " slot");
                } else {
                    Driver.fail("wear failed: " + worn + " would not go into the " + slot.getName() + " slot");
                }
            });
        });
    }

    private static void charm(Minecraft minecraft, int from, String worn) {
        LocalPlayer player = minecraft.player;
        CuriosContainer menu = (CuriosContainer) player.containerMenu;
        ItemStack stack = player.getInventory().getItem(from);
        List<CurioSlot> fitting = menu.slots.stream().filter(slot -> slot instanceof CurioSlot).map(slot -> (CurioSlot) slot)
                .filter(slot -> !slot.getSlotContext().cosmetic() && slot.mayPlace(stack)).toList();
        Optional<CurioSlot> target = fitting.stream().filter(slot -> !slot.hasItem()).findFirst().or(() -> fitting.stream().findFirst());
        if (target.isEmpty() && menu.currentPage < menu.totalPages - 1) {
            menu.nextPage();
            PacketDistributor.sendToServer(new CPacketPage(menu.containerId, true));
            Driver.drawn("the next page of charms", () -> charm(minecraft, from, worn));
            return;
        }
        if (target.isEmpty()) {
            close(minecraft);
            Driver.fail("wear failed: " + worn + " fits no charm slot");
            return;
        }
        String key = CURIOS_SLOT + target.get().getIdentifier();
        String slot = (Language.getInstance().has(key) ? Component.translatable(key).getString() : target.get().getIdentifier().replace('_', ' ')).toLowerCase(Locale.ROOT);
        swap(minecraft, menu, menuSlot(menu, player.getInventory(), from), ((Slot) target.get()).index);
        settled(minecraft, () -> {
            boolean on = target.get().getItem().getHoverName().getString().equals(worn);
            close(minecraft);
            if (on) {
                Driver.say("wear: " + worn + " in the " + slot + " slot");
            } else {
                Driver.fail("wear failed: " + worn + " would not go into the " + slot + " slot");
            }
        });
    }

    static void craft(Minecraft minecraft, String argument) {
        if (!ModList.get().isLoaded(JEI)) {
            Driver.fail("craft failed: it crafts the way JEI does, and JEI is not loaded");
            return;
        }
        Recipes.craft(minecraft, argument);
    }

    private static Optional<BlockPos> beside(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        BlockPos feet = player.blockPosition();
        return BlockPos.betweenClosedStream(feet.offset(-2, -1, -2), feet.offset(2, 1, 2)).map(BlockPos::immutable)
                .filter(pos -> !pos.equals(feet) && !pos.equals(feet.above()) && minecraft.level.getBlockState(pos).canBeReplaced()
                        && minecraft.level.getFluidState(pos).isEmpty()
                        && minecraft.level.getBlockState(pos.below()).isFaceSturdy(minecraft.level, pos.below(), Direction.UP)
                        && minecraft.level.getBlockState(pos.below()).getMenuProvider(minecraft.level, pos.below()) == null
                        && !minecraft.level.getBlockState(pos.below()).hasBlockEntity()
                        && !player.getBoundingBox().intersects(new AABB(pos)))
                .min(Comparator.<BlockPos>comparingInt(pos -> Math.abs(pos.getY() - feet.getY()))
                        .thenComparingDouble(pos -> Vec3.atCenterOf(pos).distanceTo(player.getEyePosition())));
    }

    static void put(Minecraft minecraft, String argument) {
        put(minecraft, argument, 0);
    }

    private static void put(Minecraft minecraft, String argument, int fetches) {
        String[] words = argument.strip().split(" ");
        boolean placed = words.length >= 4 && Arrays.stream(words, words.length - 3, words.length).allMatch(word -> word.matches("~?-?\\d*"));
        String name = placed ? String.join(" ", Arrays.copyOf(words, words.length - 3)) : argument.strip();
        LocalPlayer player = minecraft.player;
        Optional<BlockPos> spot = placed ? Optional.of(Driver.block(minecraft, words[words.length - 3], words[words.length - 2], words[words.length - 1]))
                : beside(minecraft);
        if (spot.isEmpty()) {
            Driver.fail("put failed: no open ground beside you to put it on");
            return;
        }
        BlockPos target = spot.get();
        int slot = slot(player, name);
        if (slot < 0) {
            Driver.fail("put failed: no " + name + " in the inventory");
            return;
        }
        if (slot >= INVENTORY || slot >= HOTBAR && fetches >= FETCHES) {
            Driver.fail("put failed: " + name + " would not come into the hotbar; hotbar " + name + " first");
            return;
        }
        if (slot >= HOTBAR) {
            settled(minecraft, () -> {
                int from = slot(player, name);
                Inventory inventory = player.getInventory();
                if (from >= HOTBAR && from < INVENTORY) {
                    minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, from, free(inventory) < 0 ? inventory.selected : free(inventory),
                            ClickType.SWAP, player);
                }
                settled(minecraft, () -> put(minecraft, argument, fetches + 1));
            });
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (!(stack.getItem() instanceof BlockItem)) {
            Driver.fail("put failed: " + stack.getHoverName().getString() + " is no block to put down");
            return;
        }
        BlockState there = minecraft.level.getBlockState(target);
        if (!there.canBeReplaced()) {
            Driver.fail("put failed: " + there.getBlock().getName().getString() + " is there already");
            return;
        }
        AABB space = new AABB(target);
        if (player.getBoundingBox().intersects(space)) {
            Driver.fail("put failed: you stand in that space");
            return;
        }
        Optional<Entity> standing = minecraft.level.getEntities(player, space, entity -> entity instanceof LivingEntity).stream().findFirst();
        if (standing.isPresent()) {
            Driver.fail("put failed: " + Senses.describe(standing.get()) + " stands there");
            return;
        }
        Vec3 eyes = player.getEyePosition();
        double reach = player.blockInteractionRange();
        String what = stack.getHoverName().getString();
        VoxelShape cover = there.getShape(minecraft.level, target);
        if (!cover.isEmpty()) {
            Vec3 aim = cover.bounds().getCenter().add(Vec3.atLowerCornerOf(target));
            if (eyes.distanceTo(aim) > reach) {
                Driver.fail("put failed: out of reach, " + Math.round(eyes.distanceTo(aim)) + " blocks");
                return;
            }
            BlockHitResult hit = minecraft.level.clip(new ClipContext(eyes, aim.add(aim.subtract(eyes).normalize().scale(BEYOND)), ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(target)) {
                Driver.fail("put failed: the " + there.getBlock().getName().getString() + " there is hidden from here");
                return;
            }
            player.getInventory().selected = slot;
            Driver.click(minecraft, hit.getLocation(), clicked -> clicked instanceof BlockHitResult block && block.getBlockPos().equals(target), true, "put", what,
                    missed -> settled(minecraft, () -> settled(minecraft, () -> placed(minecraft, target, what))));
            return;
        }
        List<Direction> against = Arrays.stream(Direction.values()).filter(side -> !minecraft.level.getBlockState(target.relative(side)).canBeReplaced()).toList();
        if (against.isEmpty()) {
            Driver.fail("put failed: nothing to put it against");
            return;
        }
        List<Direction> reached = against.stream().filter(side -> eyes.distanceTo(face(target, side)) <= reach).toList();
        if (reached.isEmpty()) {
            Driver.fail("put failed: out of reach, " + Math.round(against.stream().mapToDouble(side -> eyes.distanceTo(face(target, side))).min().orElseThrow()) + " blocks");
            return;
        }
        Optional<Direction> seen = reached.stream().filter(side -> visible(minecraft, target, side)).min(Comparator.comparingDouble(side -> eyes.distanceTo(face(target, side))));
        if (seen.isEmpty()) {
            Driver.fail("put failed: every side to put it against is hidden from here");
            return;
        }
        Direction side = seen.get();
        BlockPos base = target.relative(side);
        player.getInventory().selected = slot;
        Driver.click(minecraft, face(target, side), hit -> hit instanceof BlockHitResult block && block.getBlockPos().equals(base)
                && block.getDirection() == side.getOpposite(), true, "put", what, missed -> settled(minecraft, () -> settled(minecraft, () -> placed(minecraft, target, what))));
    }

    private static void placed(Minecraft minecraft, BlockPos target, String what) {
        BlockState now = minecraft.level.getBlockState(target);
        if (now.canBeReplaced()) {
            Driver.fail("put failed: " + what + " did not go down at " + target.toShortString());
        } else {
            Driver.say("put: " + now.getBlock().getName().getString() + " at " + target.toShortString());
        }
    }

    static Optional<BlockPos> table(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        BlockPos here = player.blockPosition();
        return BlockPos.betweenClosedStream(here.offset(-TABLE_BLOCKS, -TABLE_BLOCKS, -TABLE_BLOCKS), here.offset(TABLE_BLOCKS, TABLE_BLOCKS, TABLE_BLOCKS))
                .filter(pos -> minecraft.level.getBlockState(pos).getBlock().asItem() == Items.CRAFTING_TABLE
                        && player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= player.blockInteractionRange())
                .map(BlockPos::immutable)
                .min(Comparator.comparingDouble(pos -> player.getEyePosition().distanceTo(Vec3.atCenterOf(pos))));
    }

    private static Vec3 face(BlockPos target, Direction side) {
        return Vec3.atCenterOf(target).add(side.getStepX() * HALF, side.getStepY() * HALF, side.getStepZ() * HALF);
    }

    private static boolean visible(Minecraft minecraft, BlockPos target, Direction side) {
        Vec3 eyes = minecraft.player.getEyePosition();
        Vec3 beyond = face(target, side).add(face(target, side).subtract(eyes).normalize().scale(BEYOND));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(eyes, beyond, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target.relative(side)) && hit.getDirection() == side.getOpposite();
    }

    static void open(Minecraft minecraft, Runnable then) {
        minecraft.setScreen(new InventoryScreen(minecraft.player));
        Driver.drawn("the inventory to open", then);
    }

    static void close(Minecraft minecraft) {
        if (minecraft.screen != null) {
            minecraft.screen.onClose();
        }
    }

    static void settled(Minecraft minecraft, Runnable then) {
        Driver.unsynced();
        Driver.after(() -> Driver.answered(minecraft), "the server to answer", then);
    }

    static int menuSlot(AbstractContainerMenu menu, Inventory inventory, int index) {
        return menu.slots.stream().filter(slot -> slot.container == inventory && slot.getContainerSlot() == index).mapToInt(slot -> slot.index).findFirst()
                .orElseThrow(() -> new IllegalStateException("no slot for inventory index " + index + " in " + menu.getClass().getSimpleName()));
    }

    private static void swap(Minecraft minecraft, AbstractContainerMenu menu, int from, int to) {
        LocalPlayer player = minecraft.player;
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, from, 0, ClickType.PICKUP, player);
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, to, 0, ClickType.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, from, 0, ClickType.PICKUP, player);
        }
    }
}
