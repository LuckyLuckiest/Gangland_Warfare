package me.luckyraven.file.configuration.turf;

import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.turf.contract.TurfSoundContract;
import org.bukkit.entity.Player;

/**
 * Plays the configured capture SFX to players inside a contested turf. Sound names / volumes / pitches come from
 * {@code settings.yml} via {@link Settings}; playback runs through {@link SoundConfiguration} so the XSound fallback
 * keeps legacy sound ids working on newer Minecraft versions.
 */
public final class GanglandTurfSounds implements TurfSoundContract {

	@Override
	public void playCaptureStart(Player listener) {
		if (!Settings.isTurfCaptureSoundEnabled()) {
			return;
		}
		start().playSound(listener);
	}

	@Override
	public void playCaptureComplete(Player listener) {
		if (!Settings.isTurfCaptureSoundEnabled()) {
			return;
		}
		complete().playSound(listener);
	}

	@Override
	public void playCaptureFailed(Player listener) {
		if (!Settings.isTurfCaptureSoundEnabled()) {
			return;
		}
		failed().playSound(listener);
	}

	@Override
	public void playCaptureTick(Player listener) {
		if (!Settings.isTurfCaptureSoundEnabled()) {
			return;
		}
		tick().playSound(listener);
	}

	private SoundConfiguration tick() {
		return new SoundConfiguration(
				SoundConfiguration.SoundType.VANILLA,
				Settings.getTurfCaptureSoundTickName(),
				(float) Settings.getTurfCaptureSoundTickVolume(),
				(float) Settings.getTurfCaptureSoundTickPitch());
	}

	private SoundConfiguration start() {
		return new SoundConfiguration(
				SoundConfiguration.SoundType.VANILLA,
				Settings.getTurfCaptureSoundStartName(),
				(float) Settings.getTurfCaptureSoundStartVolume(),
				(float) Settings.getTurfCaptureSoundStartPitch());
	}

	private SoundConfiguration complete() {
		return new SoundConfiguration(
				SoundConfiguration.SoundType.VANILLA,
				Settings.getTurfCaptureSoundCompleteName(),
				(float) Settings.getTurfCaptureSoundCompleteVolume(),
				(float) Settings.getTurfCaptureSoundCompletePitch());
	}

	private SoundConfiguration failed() {
		return new SoundConfiguration(
				SoundConfiguration.SoundType.VANILLA,
				Settings.getTurfCaptureSoundFailedName(),
				(float) Settings.getTurfCaptureSoundFailedVolume(),
				(float) Settings.getTurfCaptureSoundFailedPitch());
	}
}
