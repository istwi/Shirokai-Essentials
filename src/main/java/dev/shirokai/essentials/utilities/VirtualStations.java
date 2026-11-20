package dev.shirokai.essentials.utilities;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.*;

/** Virtual stations retain vanilla recipes and return inputs without requiring a placed block. */
final class VirtualStations {
    private VirtualStations() {}
    private static void returnInputs(ScreenHandler screen, PlayerEntity player, int first, int count) {
        for (int i = first; i < first + count; i++) {
            var stack = screen.getSlot(i).takeStack(Integer.MAX_VALUE);
            if (!stack.isEmpty()) player.getInventory().offerOrDrop(stack);
        }
    }
    static ScreenHandler create(String command, int id, PlayerInventory inventory, net.minecraft.server.network.ServerPlayerEntity owner) {
        var context = ScreenHandlerContext.create(owner.getServerWorld(), owner.getBlockPos());
        return switch (command) {
            case "workbench" -> new CraftingScreenHandler(id, inventory, context) {
                @Override public boolean canUse(PlayerEntity player) { return player == owner && player.isAlive(); }
            };
            case "anvil" -> new AnvilScreenHandler(id, inventory, ScreenHandlerContext.EMPTY) {
                @Override public void onClosed(PlayerEntity player) { super.onClosed(player); returnInputs(this, player, 0, 2); }
            };
            case "grindstone" -> new GrindstoneScreenHandler(id, inventory, context) {
                @Override public boolean canUse(PlayerEntity player) { return player == owner && player.isAlive(); }
            };
            case "cortador" -> new StonecutterScreenHandler(id, inventory, context) {
                @Override public boolean canUse(PlayerEntity player) { return player == owner && player.isAlive(); }
            };
            case "tear" -> new LoomScreenHandler(id, inventory, context) {
                @Override public boolean canUse(PlayerEntity player) { return player == owner && player.isAlive(); }
            };
            default -> throw new IllegalArgumentException(command);
        };
    }
}
