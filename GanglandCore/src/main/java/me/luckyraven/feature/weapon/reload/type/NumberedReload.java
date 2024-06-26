package me.luckyraven.feature.weapon.reload.type;

import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.feature.weapon.reload.Reload;
import me.luckyraven.file.configuration.SoundConfiguration;
import org.bukkit.entity.Player;

import java.util.function.BiConsumer;

public class NumberedReload extends Reload {

	private final int amount;

	public NumberedReload(Weapon weapon, Ammunition ammunition, int amount) {
		super(weapon, ammunition);

		this.amount = amount;
	}

	@Override
	public BiConsumer<Weapon, Ammunition> executeReload(Player player) {
		return (weapon, ammunition) -> {
			SoundConfiguration.playSounds(player, weapon.getReloadCustomSoundMid(), null);
		};
	}

}
