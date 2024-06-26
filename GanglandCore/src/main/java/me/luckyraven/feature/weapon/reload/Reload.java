package me.luckyraven.feature.weapon.reload;

import me.luckyraven.exception.PluginException;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.file.configuration.SoundConfiguration;
import org.bukkit.entity.Player;

import java.util.function.BiConsumer;

public abstract class Reload implements Cloneable {

	private final Weapon     weapon;
	private final Ammunition ammunition;

	public Reload(Weapon weapon, Ammunition ammunition) {
		this.weapon     = weapon;
		this.ammunition = ammunition;
	}

	public abstract BiConsumer<Weapon, Ammunition> executeReload(Player player);

	public void reload(Player player) {
		// start reloading sound
		SoundConfiguration.playSounds(player, weapon.getReloadCustomSoundStart(), weapon.getReloadDefaultSoundBefore());

		// start the reload with the stored sound
		executeReload(player).accept(weapon, ammunition);

		// end reloading sound
		SoundConfiguration.playSounds(player, weapon.getReloadCustomSoundEnd(), weapon.getReloadCustomSoundEnd());
	}

	@Override
	public Reload clone() {
		try {
			return (Reload) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
