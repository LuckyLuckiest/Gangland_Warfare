package me.luckyraven.weapon.reload.type;

import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.timer.SequenceTimer;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.reload.Reload;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class InstantReload extends Reload {

	private SequenceTimer timer;

	public InstantReload(Weapon weapon, Ammunition ammunition) {
		super(weapon, ammunition);
	}

	@Override
	public void stopReloading() {
		if (timer == null || timer.isCancelled()) return;

		if (isReloading()) {
			super.endReloading(getCurrentPlayer());
		}

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

		ReloadData reloadData = getWeapon().getReloadData();
		if (reloadData == null) return;

		// start reloading the gun
		timer.addIntervalTaskPair(0, time -> {
			super.startReloading(player);
		});

		// the sound that plays at the middle
		long midSound = reloadData.getCooldown() / 2;
		timer.addIntervalTaskPair(midSound, time -> {
			// play the sound at the middle
			SoundConfiguration.playSounds(player, getWeapon().getSoundData().getReloadCustomMid(), null);
		});

		long remaining = Math.max(0, reloadData.getCooldown() - midSound);
		// continue execution after the sound had finished
		timer.addIntervalTaskPair(remaining, time -> {
			AmmunitionData ammunitionData = getWeapon().getAmmunitionData();
			if (ammunitionData == null) return;

			// if ammo was lost before or during the reload start (e.g. dropped), abort immediately
			boolean contains = inventory.containsAtLeast(getAmmunition().buildItem(1), ammunitionData.getConsumeRate());
			if (removeAmmunition && !contains) {
				stopReloading();
				return;
			}

			// remove the magazine the moment the reloading starts to prevent bugs
			if (removeAmmunition) {
				// consume the item
				inventory.removeItem(getAmmunition().buildItem(ammunitionData.getConsumeRate()));
			}

			// add to the weapon capacity
			getWeapon().addAmmunition(ammunitionData.getRestore());

			// update the weapon data
			int newSlot = findWeaponSlot(inventory, getWeapon());

			if (newSlot > -1) {
				ItemStack   existingItem = inventory.getItem(newSlot);
				ItemBuilder heldWeapon;

				// retrieve the existing item rather than building a new one
				heldWeapon = new ItemBuilder(
						Objects.requireNonNullElseGet(existingItem, () -> getWeapon().buildItem()));

				getWeapon().updateWeaponData(heldWeapon);
				getWeapon().updateWeapon(player, heldWeapon, newSlot);
			}

			// end reloading the gun
			super.endReloading(player);
		});

		timer.start(false);
	}

}
