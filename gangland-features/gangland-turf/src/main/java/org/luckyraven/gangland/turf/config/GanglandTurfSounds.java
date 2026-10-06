package org.luckyraven.gangland.turf.config;

import org.bukkit.entity.Player;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.gangland.turf.contract.TurfSoundContract;

/**
 * Plays the configured capture SFX to players inside a contested turf. Sound names / volumes / pitches come from
 * {@code turf/turf_settings.yml} via {@link TurfSettings}; playback runs through {@link SoundEffect} so the XSound fallback
 * keeps legacy sound ids working on newer Minecraft versions.
 */
public final class GanglandTurfSounds implements TurfSoundContract {

	private final TurfSettings settings;

	public GanglandTurfSounds(TurfSettings settings) {
		this.settings = settings;
	}

	private static SoundEffect effect(TurfSettings.Tone tone) {
		return new SoundEffect(SoundEffect.SoundType.VANILLA, tone.name(), tone.volume(), tone.pitch());
	}

	@Override
	public void playCaptureStart(Player listener) {
		if (!settings.isCaptureSoundEnabled()) {
			return;
		}
		start().playSound(listener);
	}

	@Override
	public void playCaptureComplete(Player listener) {
		if (!settings.isCaptureSoundEnabled()) {
			return;
		}
		complete().playSound(listener);
	}

	@Override
	public void playCaptureFailed(Player listener) {
		if (!settings.isCaptureSoundEnabled()) {
			return;
		}
		failed().playSound(listener);
	}

	@Override
	public void playCaptureTick(Player listener) {
		if (!settings.isCaptureSoundEnabled()) {
			return;
		}
		tick().playSound(listener);
	}

	@Override
	public void playOwnerCleared(Player listener) {
		if (!settings.isCaptureSoundEnabled()) {
			return;
		}
		unclaimed().playSound(listener);
	}

	private SoundEffect unclaimed() {
		return effect(settings.getUnclaimedSound());
	}

	private SoundEffect tick() {
		return effect(settings.getTickSound());
	}

	private SoundEffect start() {
		return effect(settings.getStartSound());
	}

	private SoundEffect complete() {
		return effect(settings.getCompleteSound());
	}

	private SoundEffect failed() {
		return effect(settings.getFailedSound());
	}
}
