package me.luckyraven.feature.weapon.reload.type;

import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.feature.weapon.reload.Reload;
import me.luckyraven.file.configuration.SoundConfiguration;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.util.function.BiConsumer;

public class InstantReload extends Reload {

	public InstantReload(Weapon weapon, Ammunition ammunition) {
		super(weapon, ammunition);
	}

	@Override
	public BiConsumer<Weapon, Ammunition> executeReload(Player player) {
		return (weapon, ammunition) -> {
			PlayerInventory inventory = player.getInventory();

			// need to take care of the cooldown
			CountdownTimer timer = new CountdownTimer(getGangland(), weapon.getReloadCooldown());
			timer.start(false);

			// consume the item
			inventory.removeItem(ammunition.buildItem(weapon.getReloadConsume()));

			// play the sound at the middle
			SoundConfiguration.playSounds(player, weapon.getReloadCustomSoundMid(), null);

			// add to the weapon capacity
			weapon.setCurrentMagCapacity(weapon.getReloadRestore());

			// update the weapon data
			ItemBuilder heldWeapon = getGangland().getInitializer().getWeaponManager().getHeldWeaponItem(player);
			if (heldWeapon == null) return;

			weapon.updateWeaponData(heldWeapon);
			weapon.updateWeapon(player, heldWeapon, inventory.getHeldItemSlot());
		};
	}

}
