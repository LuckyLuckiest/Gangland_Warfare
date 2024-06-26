package me.luckyraven.feature.weapon.reload.type;

import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.feature.weapon.reload.Reload;
import me.luckyraven.file.configuration.SoundConfiguration;
import org.bukkit.entity.Player;

import java.util.function.BiConsumer;

public class InstantReload extends Reload {

	public InstantReload(Weapon weapon, Ammunition ammunition) {
		super(weapon, ammunition);
	}

	@Override
	public BiConsumer<Weapon, Ammunition> executeReload(Player player) {
		return (weapon, ammunition) -> {
			SoundConfiguration.playSounds(player, weapon.getReloadCustomSoundMid(), null);
		};
	}

}
