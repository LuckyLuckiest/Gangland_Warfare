package me.luckyraven.util.configuration;

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
			Optional<XSound> xSoundOptional = XSound.of(sound);

			if (xSoundOptional.isPresent()) {
				Sound sound = xSoundOptional.get().get();

				if (sound == null) return false;

				player.playSound(player.getLocation(), sound, volume, pitch);
			}

			return true;
		}

		if (!ResourcePackTracker.hasResourcePack(player)) {
			return false;
		}

		// check if the custom sound hasn't played
		try {
			player.playSound(player.getLocation(), sound, volume, pitch);
		} catch (Exception exception) {
			return false;
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
