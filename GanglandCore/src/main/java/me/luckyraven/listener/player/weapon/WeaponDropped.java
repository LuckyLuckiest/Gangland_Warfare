package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.feature.weapon.Weapon;
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

		event.setCancelled(true);

		// reload the weapon
	}

}
