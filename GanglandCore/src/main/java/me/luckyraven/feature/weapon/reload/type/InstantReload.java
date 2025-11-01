package me.luckyraven.feature.weapon.reload.type;

import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.feature.weapon.reload.Reload;
import me.luckyraven.file.configuration.SoundConfiguration;
import me.luckyraven.util.timer.SequenceTimer;
import org.bukkit.entity.Player;
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
			ItemBuilder heldWeapon = new ItemBuilder(getWeapon().buildItem());

			getWeapon().updateWeaponData(heldWeapon);

			int newSlot = findWeaponSlot(inventory);

			// item is in inventory
			if (newSlot > -1) {
				getWeapon().updateWeapon(player, heldWeapon, newSlot);
			}

			// end reloading the gun
			super.endReloading(player);
		});

		timer.start(false);
	}

}
