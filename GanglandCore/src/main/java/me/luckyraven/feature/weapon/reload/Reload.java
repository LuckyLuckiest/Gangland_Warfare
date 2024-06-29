package me.luckyraven.feature.weapon.reload;

import lombok.AccessLevel;
import lombok.Getter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.file.configuration.SoundConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@Getter(value = AccessLevel.PROTECTED)
public abstract class Reload implements Cloneable {

	private final Weapon     weapon;
	private final Ammunition ammunition;

	@Getter
	private boolean reloading;

	public Reload(Weapon weapon, Ammunition ammunition) {
		this.weapon     = weapon;
		this.ammunition = ammunition;
	}

	public abstract void stopReloading();

	protected abstract void executeReload(JavaPlugin plugin, Player player, boolean removeAmmunition);

	public void reload(JavaPlugin plugin, Player player, boolean removeAmmunition) {
		// start executing reload process
		executeReload(plugin, player, removeAmmunition);
	}

	@Override
	public Reload clone() {
		try {
			return (Reload) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

	protected void startReloading(Player player) {
		// set that the weapon is reloading
		this.reloading = true;

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
		this.reloading = false;
	}

}
