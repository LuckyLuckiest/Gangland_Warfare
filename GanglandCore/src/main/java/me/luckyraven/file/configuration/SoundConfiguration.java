package me.luckyraven.file.configuration;

import com.cryptomorin.xseries.XSound;
import me.luckyraven.exception.PluginException;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record SoundConfiguration(SoundType type, @NotNull String sound, float volume, float pitch) implements
		Cloneable {

	public static void playSounds(Player player, SoundConfiguration sound1, SoundConfiguration sound2) {
		if (sound1 != null) {
			boolean sound1Executed = sound1.playSound(player);
			if (!sound1Executed && sound2 != null) sound2.playSound(player);
		} else if (sound2 != null) sound2.playSound(player);
	}

	public boolean playSound(Player player) {
		if (type == SoundType.VANILLA) {
			Optional<XSound> xSoundOptional = XSound.matchXSound(sound);

			if (xSoundOptional.isPresent()) {
				Sound sound = xSoundOptional.get().parseSound();

				if (sound == null) return false;

				player.playSound(player.getLocation(), sound, volume, pitch);
			}
		} else {
			// check if the custom sound hasn't played
			try {
				player.playSound(player.getLocation(), sound, volume, pitch);
				return true;
			} catch (Exception exception) {
				return false;
			}
		}

		return true;
	}

	@Override
	public SoundConfiguration clone() {
		try {
			return (SoundConfiguration) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

	public enum SoundType {
		VANILLA,
		CUSTOM
	}

}
