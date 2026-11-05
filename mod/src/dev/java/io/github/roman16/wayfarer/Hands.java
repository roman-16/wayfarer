package io.github.roman16.wayfarer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
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
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
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
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
    private static final String TENTS = "nomadictents";
    private static final int HOTBAR = 9;
    private static final int INVENTORY = 36;
    private static final int FETCHES = 4;
    private static final int OFF_HAND = 40;
    private static final int ARMOUR_TOP = 8;
    private static final int TABLE_BLOCKS = 5;
    private static final double HALF = 0.5;
    private static final double BEYOND = 0.1;
    private static final double CLEAR_BLOCKS = 1;
    private static final double CLEAR_NEAR = 0.5;
    private static final int CLEAR_TICKS = 64;
    private static final int TICKS_PER_SECOND = 20;
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
                .filter(index -> named(inventory.getItem(index), name, named))
                .findFirst()
                .orElse(-1);
    }

    private static boolean named(ItemStack stack, String name, Optional<Item> named) {
        return !stack.isEmpty() && (stack.getHoverName().getString().equalsIgnoreCase(name.strip()) || named.map(stack::is).orElse(false));
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
                .filter(entity -> entity != player && reach(entity, player) <= within
                        && (entity.getDisplayName().getString().equalsIgnoreCase(type.strip())
                        || id != null && !Senses.story(entity) && (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(id) || picked(entity).equals(id))))
                .min(Comparator.comparingDouble(player::distanceTo));
    }

    static double reach(Entity entity, LocalPlayer player) {
        return Math.sqrt(entity.getBoundingBox().distanceToSqr(player.getEyePosition()));
    }

    private static ResourceLocation picked(Entity entity) {
        ItemStack pick = entity.getPickResult();
        return pick == null || pick.isEmpty() ? BuiltInRegistries.ITEM.getDefaultKey() : BuiltInRegistries.ITEM.getKey(pick.getItem());
    }

    static String near(Minecraft minecraft, double within) {
        LocalPlayer player = minecraft.player;
        String near = StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity != player && reach(entity, player) <= within && (entity instanceof LivingEntity || !picked(entity).equals(
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
        open(minecraft, () -> {
            int now = slot(player, name);
            if (now < 0) {
                close(minecraft);
                Driver.fail("hotbar failed: no " + name + " in the inventory once it opened");
                return;
            }
            AbstractContainerMenu menu = player.containerMenu;
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot(menu, inventory, now), to, ClickType.SWAP, player);
            settled(minecraft, () -> {
                close(minecraft);
                if (!named(inventory.getItem(to), name, byId(name))) {
                    Driver.fail("hotbar failed: slot " + (to + 1) + " holds " + held(inventory.getItem(to)) + " after the move, not " + name);
                    return;
                }
                Driver.say("hotbar: " + held(inventory.getItem(to)) + " in slot " + (to + 1));
            });
        });
    }

    private static int spare(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        return IntStream.range(0, HOTBAR).filter(index -> inventory.getItem(index).isEmpty()).findFirst()
                .orElseGet(() -> IntStream.range(0, HOTBAR).filter(index -> index != inventory.selected && !needed(player, inventory.getItem(index))).reduce((a, b) -> b)
                        .orElse(inventory.selected));
    }

    private static boolean needed(LocalPlayer player, ItemStack stack) {
        return stack.getMaxStackSize() == 1 || stack.getFoodProperties(player) != null;
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
                int left = player.getItemBySlot(slot).getHoverName().getString().equals(worn) ? -1 : slot(player, worn);
                if (left >= 0 && left < INVENTORY) {
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot(menu, inventory, left), 0, ClickType.QUICK_MOVE, player);
                }
                settled(minecraft, () -> {
                    boolean on = player.getItemBySlot(slot).getHoverName().getString().equals(worn);
                    close(minecraft);
                    if (on) {
                        Driver.say("wear: " + worn + " on, in the " + slot.getName() + " slot" + (left >= 0 ? ", with a shift-click" : ""));
                    } else {
                        Driver.fail("wear failed: " + worn + " would not go into the " + slot.getName() + " slot, by hand or with a shift-click");
                    }
                });
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

    private static Optional<BlockPos> beside(Minecraft minecraft, @Nullable BlockItem item) {
        LocalPlayer player = minecraft.player;
        BlockPos feet = player.blockPosition();
        boolean walls = item instanceof StandingAndWallBlockItem;
        return BlockPos.betweenClosedStream(feet.offset(-2, -1, -2), feet.offset(2, 1, 2)).map(BlockPos::immutable)
                .filter(pos -> !pos.equals(feet) && !pos.equals(feet.above()) && givesWay(minecraft.level.getBlockState(pos))
                        && minecraft.level.getFluidState(pos).isEmpty()
                        && !player.getBoundingBox().intersects(new AABB(pos))
                        && !doorway(minecraft, pos)
                        && (floor(minecraft, pos) || walls && wall(minecraft, pos).isPresent())
                        && (item == null || footprint(minecraft, item, pos).stream().allMatch(cell -> room(minecraft, cell).isEmpty())))
                .min(Comparator.<BlockPos>comparingInt(pos -> floor(minecraft, pos) ? 0 : 1).thenComparingInt(pos -> Math.abs(pos.getY() - feet.getY()))
                        .thenComparingDouble(pos -> Vec3.atCenterOf(pos).distanceTo(player.getEyePosition())));
    }

    private static boolean floor(Minecraft minecraft, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState ground = minecraft.level.getBlockState(below);
        LocalPlayer player = minecraft.player;
        return (ground.isFaceSturdy(minecraft.level, below, Direction.UP) || drift(ground)) && ground.getMenuProvider(minecraft.level, below) == null
                && !ground.hasBlockEntity() && player.getEyePosition().distanceTo(face(pos, Direction.DOWN)) <= player.blockInteractionRange()
                && (minecraft.level.getBlockState(pos).getShape(minecraft.level, pos).isEmpty() ? visible(minecraft, pos, Direction.DOWN) : covered(minecraft, pos));
    }

    private static Optional<Direction> wall(Minecraft minecraft, BlockPos pos) {
        LocalPlayer player = minecraft.player;
        return Direction.Plane.HORIZONTAL.stream().filter(side -> minecraft.level.getBlockState(pos.relative(side)).isFaceSturdy(minecraft.level, pos.relative(side), side.getOpposite())
                && player.getEyePosition().distanceTo(face(pos, side)) <= player.blockInteractionRange() && visible(minecraft, pos, side)).findFirst();
    }

    private static boolean drift(BlockState state) {
        return state.hasProperty(SnowLayerBlock.LAYERS) && state.getValue(SnowLayerBlock.LAYERS) > 1;
    }

    private static boolean doorway(Minecraft minecraft, BlockPos pos) {
        return BlockPos.betweenClosedStream(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)).anyMatch(near -> {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(near).getBlock());
            return key.getNamespace().equals(TENTS) && key.getPath().endsWith("_door");
        });
    }

    static void put(Minecraft minecraft, String argument) {
        put(minecraft, argument, 0);
    }

    private static void put(Minecraft minecraft, String argument, int fetches) {
        String[] words = argument.strip().split(" ");
        boolean placed = words.length >= 4 && Arrays.stream(words, words.length - 3, words.length).allMatch(word -> word.matches("~?-?\\d*"));
        String name = placed ? String.join(" ", Arrays.copyOf(words, words.length - 3)) : argument.strip();
        LocalPlayer player = minecraft.player;
        int held = slot(player, name);
        BlockItem putting = held >= 0 && player.getInventory().getItem(held).getItem() instanceof BlockItem item ? item : null;
        Optional<BlockPos> spot = placed ? Optional.of(Driver.block(minecraft, words[words.length - 3], words[words.length - 2], words[words.length - 1]))
                : beside(minecraft, putting);
        if (spot.isEmpty()) {
            Driver.fail("put failed: no open ground beside you to put it on");
            return;
        }
        BlockPos target = drift(minecraft.level.getBlockState(spot.get())) ? spot.get().above() : spot.get();
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
                    minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, from, spare(player), ClickType.SWAP, player);
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
        if (!givesWay(there)) {
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
        for (BlockPos cell : footprint(minecraft, (BlockItem) stack.getItem(), target)) {
            Optional<String> taken = room(minecraft, cell);
            if (taken.isPresent()) {
                Driver.fail("put failed: no room for all of the " + stack.getHoverName().getString() + " at " + cell.toShortString() + ": " + taken.get());
                return;
            }
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
        List<Direction> against = Arrays.stream(Direction.values()).filter(side -> !givesWay(minecraft.level.getBlockState(target.relative(side)))).toList();
        if (against.isEmpty()) {
            Driver.fail("put failed: nothing to put it against");
            return;
        }
        List<Direction> reached = against.stream().filter(side -> eyes.distanceTo(face(target, side)) <= reach).toList();
        if (reached.isEmpty()) {
            Driver.fail("put failed: out of reach, " + Math.round(against.stream().mapToDouble(side -> eyes.distanceTo(face(target, side))).min().orElseThrow()) + " blocks");
            return;
        }
        Optional<Direction> seen = reached.stream().filter(side -> visible(minecraft, target, side))
                .min(Comparator.comparing((Direction side) -> opens(minecraft, target.relative(side))).thenComparingDouble(side -> eyes.distanceTo(face(target, side))));
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

    private static boolean opens(Minecraft minecraft, BlockPos pos) {
        return minecraft.level.getBlockState(pos).getMenuProvider(minecraft.level, pos) != null || minecraft.level.getBlockEntity(pos) instanceof MenuProvider;
    }

    private static List<BlockPos> footprint(Minecraft minecraft, BlockItem item, BlockPos target) {
        BlockState state = item.getBlock().defaultBlockState();
        if (state.hasProperty(BlockStateProperties.BED_PART)) {
            Vec3 way = Vec3.atCenterOf(target).subtract(minecraft.player.getEyePosition());
            return List.of(target.relative(Direction.getNearest(way.x, 0, way.z)));
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return List.of(target.above());
        }
        return List.of();
    }

    private static Optional<String> room(Minecraft minecraft, BlockPos cell) {
        BlockState there = minecraft.level.getBlockState(cell);
        if (!givesWay(there)) {
            return Optional.of(there.getBlock().getName().getString() + " is there");
        }
        if (minecraft.player.getBoundingBox().intersects(new AABB(cell))) {
            return Optional.of("you stand there");
        }
        return minecraft.level.getEntities(minecraft.player, new AABB(cell), entity -> entity instanceof LivingEntity).stream().findFirst()
                .map(entity -> Senses.describe(entity) + " stands there");
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

    static boolean givesWay(BlockState state) {
        return state.canBeReplaced() && !(state.hasProperty(SnowLayerBlock.LAYERS) && state.getValue(SnowLayerBlock.LAYERS) > 1);
    }

    private static boolean covered(Minecraft minecraft, BlockPos target) {
        Vec3 eyes = minecraft.player.getEyePosition();
        Vec3 aim = minecraft.level.getBlockState(target).getShape(minecraft.level, target).bounds().getCenter().add(Vec3.atLowerCornerOf(target));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(eyes, aim.add(aim.subtract(eyes).normalize().scale(BEYOND)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, minecraft.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }

    private static boolean visible(Minecraft minecraft, BlockPos target, Direction side) {
        Vec3 eyes = minecraft.player.getEyePosition();
        Vec3 beyond = face(target, side).add(face(target, side).subtract(eyes).normalize().scale(BEYOND));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(eyes, beyond, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target.relative(side)) && hit.getDirection() == side.getOpposite();
    }

    static void clear(Minecraft minecraft, Runnable then) {
        LocalPlayer player = minecraft.player;
        Vec3 eyes = player.getEyePosition();
        Optional<Entity> around = StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity != player && entity != player.getVehicle() && !entity.hasPassenger(player) && entity.isPickable()
                        && entity.getBoundingBox().inflate(BEYOND).contains(eyes))
                .findFirst();
        if (around.isEmpty() || player.isPassenger()) {
            then.run();
            return;
        }
        Entity body = around.get();
        AABB box = body.getBoundingBox();
        Vec3 centre = box.getCenter();
        Vec3 away = new Vec3(player.getX() - centre.x, 0, player.getZ() - centre.z);
        away = away.lengthSqr() < BEYOND * BEYOND ? Vec3.directionFromRotation(0, player.getYRot()) : away.normalize();
        Vec3 point = new Vec3(centre.x + away.x * (Math.max(box.getXsize(), box.getZsize()) / 2 + CLEAR_BLOCKS), player.getY(),
                centre.z + away.z * (Math.max(box.getXsize(), box.getZsize()) / 2 + CLEAR_BLOCKS));
        long from = minecraft.level.getGameTime();
        Driver.after(() -> {
            boolean free = !body.getBoundingBox().inflate(BEYOND).contains(player.getEyePosition()) && Math.hypot(point.x - player.getX(), point.z - player.getZ()) <= CLEAR_NEAR;
            if (free || body.isRemoved() || minecraft.level.getGameTime() - from > CLEAR_TICKS) {
                minecraft.options.keyUp.setDown(false);
                return true;
            }
            player.setYRot((float) (Mth.atan2(point.z - player.getZ(), point.x - player.getX()) * Mth.RAD_TO_DEG) - 90);
            minecraft.options.keyUp.setDown(true);
            Driver.progress();
            return false;
        }, "stepping out of " + Senses.describe(body) + "'s space", () -> {
            if (body.getBoundingBox().inflate(BEYOND).contains(player.getEyePosition())) {
                Driver.fail("stepping out failed: still inside " + Senses.describe(body) + "'s space after " + CLEAR_TICKS / TICKS_PER_SECOND + " s");
                return;
            }
            Driver.say("stepped out of " + Senses.describe(body) + "'s space to " + player.blockPosition().toShortString());
            then.run();
        });
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
