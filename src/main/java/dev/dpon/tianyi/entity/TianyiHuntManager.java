package dev.dpon.tianyi.entity;

import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide registry of hunted players. When a Tianyi's affinity reaches
 * -200 she registers her owner here; every Tianyi on the server will then
 * materialize the recorded weapon and hunt down that player.
 */
public final class TianyiHuntManager {
    private static final Map<UUID, ItemStack> HUNT_WEAPONS = new HashMap<>();

    private TianyiHuntManager() {}

    public static void startHunt(UUID playerId, ItemStack weapon) {
        HUNT_WEAPONS.put(playerId, weapon.isEmpty() ? ItemStack.EMPTY : weapon.copy());
    }

    public static void endHunt(UUID playerId) {
        HUNT_WEAPONS.remove(playerId);
    }

    public static boolean isHunted(UUID playerId) {
        return HUNT_WEAPONS.containsKey(playerId);
    }

    public static ItemStack getWeaponFor(UUID playerId) {
        return HUNT_WEAPONS.getOrDefault(playerId, ItemStack.EMPTY);
    }

    public static Map<UUID, ItemStack> allHunts() {
        return HUNT_WEAPONS;
    }
}