package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.feature.weapon.Weapon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

public class WeaponDropped implements Listener {

	private final Gangland gangland;

	public WeaponDropped(Gangland gangland) {
		this.gangland = gangland;
	}

	@EventHandler
	public void onPlayerDrop(PlayerDropItemEvent event) {
		ItemStack item = event.getItemDrop().getItemStack();

		// check if it was a weapon only
		if (!Weapon.isWeapon(item)) return;

		Player player = event.getPlayer();

		// drop the weapon normally
		if (!player.isSneaking()) return;

		// check if the player doesn't have the item
		boolean found = false;

		if (!found) return;

		// drop the weapon if the player has ammunition item for it
		event.setCancelled(true);
	}

}
