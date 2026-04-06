package me.luckyraven.weapon.reload.type;

import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.timer.SequenceTimer;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.reload.Reload;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

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

		if (isReloading()) {
			super.endReloading(getCurrentPlayer());
		}

		timer.stop();
		timer = null;
	}

	@Override
	public String toString() {
		return String.format("NumberedReload{isReloading=%s, amount=%d, timer=%s}", isReloading(), amount, timer);
	}

	@Override
	protected void executeReload(JavaPlugin plugin, Player player, boolean removeAmmunition) {
		PlayerInventory inventory = player != null ? player.getInventory() : null;

		timer = new SequenceTimer(plugin);

		// start reloading the gun
		timer.addIntervalTaskPair(0, time -> {
			super.startReloading(player);
		});

		AmmunitionData ammunitionData = getWeapon().getAmmunitionData();
		if (ammunitionData == null) return;

		// calculate the number of inserts according to the mag capacity
		int leftToInsert       = ammunitionData.getMaxMagCapacity() - getWeapon().getCurrentMagCapacity();
		int numberOfInsertions = leftToInsert / ammunitionData.getRestore();

		if (inventory != null) {
			// limit insertions by how much ammo the player actually carries
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
		}
		// NPC path (inventory == null): use full numberOfInsertions — NPCs have unlimited ammo supply

		ReloadData reloadData = getWeapon().getReloadData();
		if (reloadData == null) return;

		for (int i = 0; i < numberOfInsertions; ++i) {
			timer.addIntervalTaskPair(reloadData.getCooldown(), time -> {
				if (!isReloading()) return;

				if (inventory != null) {
					// if ammo was dropped mid-reload, abort the remaining insertions
					boolean contains = inventory.containsAtLeast(getAmmunition().buildItem(1), amount);
					if (removeAmmunition && !contains) {
						stopReloading();
						return;
					}
				}

				// reload middle sound
				if (player != null) {
					SoundConfiguration.playSounds(player, getWeapon().getSoundData().getReloadCustomMid(), null);
				}

				if (inventory != null && removeAmmunition) {
					inventory.removeItem(getAmmunition().buildItem(amount));
				}

				// add to the weapon capacity
				getWeapon().addAmmunition(ammunitionData.getRestore());

				if (inventory != null) {
					// update the weapon data in the player's inventory
					int newSlot = findWeaponSlot(inventory, getWeapon());
					if (newSlot > -1) {
						ItemStack existingItem = inventory.getItem(newSlot);
						ItemBuilder heldWeapon = new ItemBuilder(
								Objects.requireNonNullElseGet(existingItem, () -> getWeapon().buildItem()));
						getWeapon().updateWeaponData(heldWeapon);
						getWeapon().updateWeapon(player, heldWeapon, newSlot);
					}
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
