package me.luckyraven.weapon.reload.type;

import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.timer.SequenceTimer;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.reload.Reload;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public class InstantReload extends Reload {

	private SequenceTimer timer;

	public InstantReload(Weapon weapon, Ammunition ammunition) {
		super(weapon, ammunition);
	}

	@Override
	public void stopReloading() {
		if (timer == null || timer.isCancelled()) return;

		timer.stop();
		timer = null;
	}

	@Override
	public String toString() {
		return String.format("InstantReload{isReloading=%s, timer=%s}", isReloading(), timer);
	}

	@Override
	protected void executeReload(JavaPlugin plugin, Player player, boolean removeAmmunition) {
		PlayerInventory inventory = player.getInventory();

		timer = new SequenceTimer(plugin);

		// start reloading the gun
		timer.addIntervalTaskPair(0, time -> {
			super.startReloading(player);

			// TODO a bug that doesn't allow the weapon to leave reload state even after explicitly saying to end the reload
//			boolean contains = inventory.containsAtLeast(getAmmunition().buildItem(1), getWeapon().getReloadConsume());
//			if (removeAmmunition && !contains) {
//				stopReloading();
//				super.endReloading(player);
//				return;
//			}

			// remove the magazine the moment the reloading starts to prevent bugs
			if (removeAmmunition) {
				// consume the item
				inventory.removeItem(getAmmunition().buildItem(getWeapon().getReloadConsume()));
			}
		});

		// the sound that plays at the middle
		long midSound = getWeapon().getReloadCooldown() / 2;
		timer.addIntervalTaskPair(midSound, time -> {
			// play the sound at the middle
			SoundConfiguration.playSounds(player, getWeapon().getReloadCustomSoundMid(), null);
		});

		long remaining = Math.max(0, getWeapon().getReloadCooldown() - midSound);
		// continue execution after the sound had finished
		timer.addIntervalTaskPair(remaining, time -> {
			// add to the weapon capacity
			getWeapon().addAmmunition(getWeapon().getReloadRestore());

			// update the weapon data
			int newSlot = findWeaponSlot(inventory, getWeapon());

			if (newSlot > -1) {
				ItemStack   existingItem = inventory.getItem(newSlot);
				ItemBuilder heldWeapon;

				if (existingItem != null) {
					// retrieve the existing item rather than building a new one
					heldWeapon = new ItemBuilder(existingItem);
				} else {
					heldWeapon = new ItemBuilder(getWeapon().buildItem());
				}

				getWeapon().updateWeaponData(heldWeapon);
				getWeapon().updateWeapon(player, heldWeapon, newSlot);
			}

			// end reloading the gun
			super.endReloading(player);
		});

		timer.start(false);
	}

}
