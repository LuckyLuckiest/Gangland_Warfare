package me.luckyraven.weapon.reload.type;

import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.timer.SequenceTimer;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.reload.Reload;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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

		int numberOfAmmunition = 0;
		for (int i = 0; i < inventory.getSize(); i++) {
			ItemStack item = inventory.getItem(i);

			if (item == null || item.getType() == Material.AIR || !Ammunition.isAmmunition(item)) continue;

			Ammunition ammo = getAmmunition();

			if (item.equals(ammo.buildItem(item.getAmount()))) {
				numberOfAmmunition += item.getAmount();
			}
		}

		int maxPossibleInsertions = numberOfAmmunition / amount;

		numberOfInsertions = Math.min(numberOfInsertions, maxPossibleInsertions);

		for (int i = 0; i < numberOfInsertions; ++i) {
			timer.addIntervalTaskPair(getWeapon().getReloadCooldown(), time -> {
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
			});
		}

		// end reloading the gun
		timer.addIntervalTaskPair(1, time -> {
			super.endReloading(player);
		});

		timer.start(false);
	}

}
