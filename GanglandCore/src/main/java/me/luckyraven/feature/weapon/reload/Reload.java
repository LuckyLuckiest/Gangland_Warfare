package me.luckyraven.feature.weapon.reload;

import lombok.AccessLevel;
import lombok.Getter;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.exception.PluginException;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.WeaponTag;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.file.configuration.SoundConfiguration;
import me.luckyraven.util.ChatUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;

@Getter(value = AccessLevel.PROTECTED)
public abstract class Reload implements Cloneable {

	private final Weapon     weapon;
	private final Ammunition ammunition;

	private AtomicBoolean reloading;

	public Reload(Weapon weapon, Ammunition ammunition) {
		this.weapon     = weapon;
		this.ammunition = ammunition;
		this.reloading  = new AtomicBoolean();
	}

	public abstract void stopReloading();

	protected abstract void executeReload(JavaPlugin plugin, Player player, boolean removeAmmunition);

	public void reload(JavaPlugin plugin, Player player, boolean removeAmmunition) {
		// reload the weapon action bar status
		ChatUtil.sendActionBar(plugin, player, weapon.getReloadActionBarReloading(), weapon.getReloadCooldown());

		// start executing the reload process
		executeReload(plugin, player, removeAmmunition);
	}

	@Override
	public Reload clone() {
		try {
			Reload clone = (Reload) super.clone();

			clone.reloading = new AtomicBoolean(this.reloading.get());

			return clone;
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

	public boolean isReloading() {
		return reloading.get();
	}

	protected void startReloading(Player player) {
		// set that the weapon is reloading
		this.reloading.set(true);

		// open the reload chamber action bar status
		ChatUtil.sendActionBar(player, weapon.getReloadActionBarOpening());

		// start reloading sound
		SoundConfiguration.playSounds(player, weapon.getReloadCustomSoundStart(), weapon.getReloadDefaultSoundBefore());

		// scope the player and make them slow down
		weapon.scope(player, false);
	}

	protected void endReloading(Player player) {
		// end reloading sound
		SoundConfiguration.playSounds(player, weapon.getReloadCustomSoundEnd(), weapon.getReloadDefaultSoundAfter());

		// un-scope the player to resume the showdown
		weapon.unScope(player, true);

		// set the weapon as not reloading
		this.reloading.set(false);
	}

	/**
	 * Searches for the weapon's slot in the player's inventory by UUID.
	 *
	 * @param inventory the player's inventory to search
	 *
	 * @return the slot index where the weapon is located, or -1 if not found
	 */
	protected int findWeaponSlot(PlayerInventory inventory) {
		String      weaponUUID = Weapon.getTagProperName(WeaponTag.UUID);
		ItemStack[] contents   = inventory.getContents();

		for (int i = 0; i < contents.length; i++) {
			ItemStack item = contents[i];
			if (item == null || item.getType().isAir()) continue;

			ItemBuilder itemBuilder = new ItemBuilder(item);
			if (!itemBuilder.hasNBTTag("UUID")) continue;

			String uuid = itemBuilder.getStringTagData("UUID");
			if (weaponUUID.equals(uuid)) {
				return i;
			}
		}

		return -1;
	}

}
