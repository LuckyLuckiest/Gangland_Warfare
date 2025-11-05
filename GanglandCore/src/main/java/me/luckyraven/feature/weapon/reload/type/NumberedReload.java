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

public class NumberedReload extends Reload {

	private final int amount;

	private SequenceTimer timer;

	public NumberedReload(Weapon weapon, Ammunition ammunition, int amount) {
		super(weapon, ammunition);

		this.amount = amount;
	}

	@Override
	public void stopReloading() {
		if (timer == null || timer.isCancelled()) return;

		timer.stop();
		timer = null;
	}

	@Override
	public String toString() {
		return String.format("NumberedReload{isReloading=%s, amount=%d, timer=%s}", isReloading(), amount, timer);
	}

	@Override
	protected void executeReload(JavaPlugin plugin, Player player, boolean removeAmmunition) {
		PlayerInventory inventory = player.getInventory();

		timer = new SequenceTimer(plugin);

		// start reloading the gun
		timer.addIntervalTaskPair(0, time -> {
			super.startReloading(player);
		});

		// calculate the number of inserts according to the mag capacity
		int leftToInsert       = getWeapon().getMaxMagCapacity() - getWeapon().getCurrentMagCapacity();
		int numberOfInsertions = leftToInsert / getWeapon().getReloadRestore();

		for (int i = 0; i < numberOfInsertions; ++i) {
			timer.addIntervalTaskPair(getWeapon().getReloadCooldown(), time -> {
				// check if the user has the amount necessary to reload
				if (!inventory.containsAtLeast(getAmmunition().buildItem(), amount)) {
					// stop the timer
					time.stop();
					return;
				}

				// reload middle sound
				SoundConfiguration.playSounds(player, getWeapon().getReloadCustomSoundMid(), null);

				// take the item
				if (removeAmmunition) {
					// consume the item
					inventory.removeItem(getAmmunition().buildItem(amount));
				}

				// add to the weapon capacity
				getWeapon().addAmmunition(getWeapon().getReloadRestore());

				// update the weapon data
				ItemBuilder heldWeapon = new ItemBuilder(getWeapon().buildItem());

				getWeapon().updateWeaponData(heldWeapon);

				int newSlot = findWeaponSlot(inventory, getWeapon());

				if (newSlot > -1) {
					getWeapon().updateWeapon(player, heldWeapon, newSlot);
				}
			});
		}

		// end reloading the gun
		timer.addIntervalTaskPair(1, time -> {
			super.endReloading(player);
		});

		timer.start(false);
	}

}
