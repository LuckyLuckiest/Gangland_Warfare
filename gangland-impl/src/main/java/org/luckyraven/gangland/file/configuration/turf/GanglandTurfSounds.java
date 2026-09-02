package org.luckyraven.gangland.file.configuration.turf;

import org.bukkit.entity.Player;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.contract.TurfSoundContract;

/**
 * Plays the configured capture SFX to players inside a contested turf. Sound names / volumes / pitches come from
 * {@code settings.yml} via {@link Settings}; playback runs through {@link SoundEffect} so the XSound fallback
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

	@Override
	public void playOwnerCleared(Player listener) {
		if (!Settings.isTurfCaptureSoundEnabled()) {
			return;
		}
		unclaimed().playSound(listener);
	}

	private SoundEffect unclaimed() {
		return new SoundEffect(
				SoundEffect.SoundType.VANILLA,
				Settings.getTurfCaptureSoundUnclaimedName(),
				(float) Settings.getTurfCaptureSoundUnclaimedVolume(),
				(float) Settings.getTurfCaptureSoundUnclaimedPitch());
	}

	private SoundEffect tick() {
		return new SoundEffect(
				SoundEffect.SoundType.VANILLA,
				Settings.getTurfCaptureSoundTickName(),
				(float) Settings.getTurfCaptureSoundTickVolume(),
				(float) Settings.getTurfCaptureSoundTickPitch());
	}

	private SoundEffect start() {
		return new SoundEffect(
				SoundEffect.SoundType.VANILLA,
				Settings.getTurfCaptureSoundStartName(),
				(float) Settings.getTurfCaptureSoundStartVolume(),
				(float) Settings.getTurfCaptureSoundStartPitch());
	}

	private SoundEffect complete() {
		return new SoundEffect(
				SoundEffect.SoundType.VANILLA,
				Settings.getTurfCaptureSoundCompleteName(),
				(float) Settings.getTurfCaptureSoundCompleteVolume(),
				(float) Settings.getTurfCaptureSoundCompletePitch());
	}

	private SoundEffect failed() {
		return new SoundEffect(
				SoundEffect.SoundType.VANILLA,
				Settings.getTurfCaptureSoundFailedName(),
				(float) Settings.getTurfCaptureSoundFailedVolume(),
				(float) Settings.getTurfCaptureSoundFailedPitch());
	}
}
