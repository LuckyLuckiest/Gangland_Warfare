package org.luckyraven.gangland.copsncrooks.setup;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The per-admin selections of the setup wand, plus what makes an item the wand and a player allowed to use it. */
public final class SetupSelections {

	public static final String WAND_NBT_KEY = "cnc_setup_wand";
	/** The node of {@code /glw cop setup} (the command namespace + path, like the other cop sub-commands). */
	public static final String PERMISSION   = "gangland.command.cop.setup";

	private final Map<UUID, SetupSelection> selections = new ConcurrentHashMap<>();

	public SetupSelection get(UUID admin) {
		return selections.computeIfAbsent(admin, id -> new SetupSelection());
	}

	public @Nullable SetupSelection peek(UUID admin) {
		return selections.get(admin);
	}

	public void clear(UUID admin) {
		selections.remove(admin);
	}

	public static boolean isWand(@Nullable ItemStack item) {
		return item != null && item.getType() != Material.AIR && new ItemBuilder(item).hasNBTTag(WAND_NBT_KEY);
	}

	/** Allowed to use the setup tools and holding the wand in the main hand. */
	public static boolean holdsWand(Player player) {
		return player.hasPermission(PERMISSION) && isWand(player.getInventory().getItemInMainHand());
	}
}
