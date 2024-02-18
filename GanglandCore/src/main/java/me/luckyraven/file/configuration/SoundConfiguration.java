package me.luckyraven.file.configuration;

import com.cryptomorin.xseries.XSound;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record SoundConfiguration(SoundType type, @NotNull String sound, float volume, float pitch) {

	public boolean playSound(Player player) {
		if (type == SoundType.VANILLA) {
			Optional<XSound> xSoundOptional = XSound.matchXSound(sound);

			if (xSoundOptional.isPresent()) {
				Sound sound = xSoundOptional.get().parseSound();

				if (sound == null) return false;

				player.playSound(player.getLocation(), sound, volume, pitch);
			}
		} else player.playSound(player.getLocation(), sound, volume, pitch);

		return true;
	}

	public enum SoundType {
		VANILLA,
		CUSTOM
	}

}
