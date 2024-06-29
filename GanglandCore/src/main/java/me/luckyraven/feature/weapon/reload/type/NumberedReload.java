package me.luckyraven.feature.weapon.reload.type;

import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.feature.weapon.reload.Reload;
import me.luckyraven.util.timer.SequenceTimer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicInteger;

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
	protected void executeReload(JavaPlugin plugin, Player player, boolean removeAmmunition) {
		PlayerInventory inventory = player.getInventory();
		AtomicInteger   slot      = new AtomicInteger();

		timer = new SequenceTimer(plugin);

		// start reloading the gun
		timer.addIntervalTaskPair(0, time -> {
			super.startReloading(player);

			// save the slot of the weapon
			slot.set(inventory.getHeldItemSlot());
		});

		// calculate the number of inserts according to the time and restore value
		double totalSeconds = Math.ceil((double) getWeapon().getMaxMagCapacity() / getWeapon().getReloadRestore() *
										getWeapon().getReloadCooldown());

		int numberOfIntervals = (int) Math.ceil(totalSeconds / getWeapon().getReloadCooldown());

		for (int i = 0; i < numberOfIntervals; ++i) {
			timer.addIntervalTaskPair(getWeapon().getReloadCooldown(), time -> {
				// check if the user has the amount necessary to reload
				if (!inventory.containsAtLeast(getAmmunition().buildItem(), amount)) {
					// stop the timer
					time.stop();
					return;
				}

				// take the item
				if (removeAmmunition)
					// consume the item
					inventory.removeItem(getAmmunition().buildItem(amount));

				// add to the weapon capacity
				getWeapon().addAmmunition(getWeapon().getReloadRestore());

				// update the weapon data
				ItemBuilder heldWeapon = new ItemBuilder(getWeapon().buildItem());

				getWeapon().updateWeaponData(heldWeapon);
				getWeapon().updateWeapon(player, heldWeapon, slot.get());
			});
		}

		// end reloading the gun
		timer.addIntervalTaskPair(0, time -> {
			super.endReloading(player);
		});

		timer.start(false);
	}

}
