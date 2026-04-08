package me.luckyraven.gadget.repair;

import me.luckyraven.gadget.repair.anvil.RepairAnvilGui;
import me.luckyraven.item.contract.RepairService;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.WeaponTag;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Implements the gangland-item {@link RepairService} contract by recognising a held weapon and opening the configured
 * {@link RepairAnvilGui} for it. Mirrors the original behaviour of {@code RepairListener} so the listener itself can
 * stay free of gangland-gadget/-weapon imports.
 */
public class GanglandRepairService implements RepairService {

	private final JavaPlugin     plugin;
	private final WeaponService  weaponService;
	private final RepairAnvilGui repairAnvilGui;

	public GanglandRepairService(@NotNull JavaPlugin plugin,
	                             @NotNull WeaponService weaponService,
	                             @NotNull RepairAnvilGui repairAnvilGui) {
		this.plugin         = plugin;
		this.weaponService  = weaponService;
		this.repairAnvilGui = repairAnvilGui;
	}

	@Override
	public boolean tryOpenRepairFor(Player player, ItemStack heldItem) {
		if (heldItem == null || !weaponService.isWeapon(heldItem)) return false;

		Weapon weapon = resolveWeapon(heldItem, player);
		if (weapon == null) return false;

		Bukkit.getScheduler().runTask(plugin, () -> repairAnvilGui.open(player, weapon, heldItem));
		return true;
	}

	@Nullable
	private Weapon resolveWeapon(@NotNull ItemStack item, @NotNull Player player) {
		ItemBuilder builder = new ItemBuilder(item);
		String      uuidStr = builder.getStringTagData(Weapon.getTagProperName(WeaponTag.UUID));
		String      type    = builder.getStringTagData(Weapon.getTagProperName(WeaponTag.WEAPON));

		if (uuidStr == null || type == null) return null;

		try {
			UUID uuid = UUID.fromString(uuidStr);
			return weaponService.getWeapon(player, uuid, type);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

}
