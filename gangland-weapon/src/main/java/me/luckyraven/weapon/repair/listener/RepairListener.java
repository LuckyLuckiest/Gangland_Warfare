package me.luckyraven.weapon.repair.listener;

import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.WeaponTag;
import me.luckyraven.weapon.repair.RepairManager;
import me.luckyraven.weapon.repair.anvil.RepairAnvilGui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class RepairListener implements Listener {

	private final JavaPlugin     plugin;
	private final WeaponService  weaponService;
	private final RepairManager  repairManager;
	private final RepairAnvilGui repairAnvilGui;

	public RepairListener(@NotNull JavaPlugin plugin, @NotNull WeaponService weaponService,
						  @NotNull RepairManager repairManager, @NotNull RepairAnvilGui repairAnvilGui) {
		this.plugin         = plugin;
		this.weaponService  = weaponService;
		this.repairManager  = repairManager;
		this.repairAnvilGui = repairAnvilGui;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onAnvilOpen(InventoryOpenEvent event) {
		if (event.getInventory().getType() != InventoryType.ANVIL) return;
		if (!(event.getPlayer() instanceof Player player)) return;

		ItemStack heldItem = player.getInventory().getItemInMainHand();
		if (!weaponService.isWeapon(heldItem)) return;

		Weapon weapon = getWeaponFromItem(heldItem, player);
		if (weapon == null) return;

		event.setCancelled(true);

		Bukkit.getScheduler().runTask(plugin, () -> repairAnvilGui.open(player, weapon, heldItem));
	}

	@Nullable
	private Weapon getWeaponFromItem(@NotNull ItemStack item, @NotNull Player player) {
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
