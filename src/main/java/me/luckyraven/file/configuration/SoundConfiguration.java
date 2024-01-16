package me.luckyraven.file.configuration;

import com.cryptomorin.xseries.XSound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

public record SoundConfiguration(SoundType type, @NotNull String sound, float volume, float pitch) {

	public void playSound(Player player) {
		if (type == SoundType.VANILLA) {
			Optional<XSound> xSoundOptional = XSound.matchXSound(sound);

			xSoundOptional.ifPresent(
					xSound -> player.playSound(player.getLocation(), Objects.requireNonNull(xSound.parseSound()),
											   volume, pitch));
		} else player.playSound(player.getLocation(), sound, volume, pitch);
	}

	public enum SoundType {
		VANILLA,
		CUSTOM
	}

}
