package org.luckyraven.gangland.turf.config;

import org.bukkit.entity.Player;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.gangland.turf.contract.TurfSoundContract;

import java.util.function.Function;

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
		play(listener, TurfSettings::getStartSound);
	}

	@Override
	public void playCaptureComplete(Player listener) {
		play(listener, TurfSettings::getCompleteSound);
	}

	@Override
	public void playCaptureFailed(Player listener) {
		play(listener, TurfSettings::getFailedSound);
	}

	@Override
	public void playCaptureTick(Player listener) {
		play(listener, TurfSettings::getTickSound);
	}

	@Override
	public void playOwnerCleared(Player listener) {
		play(listener, TurfSettings::getUnclaimedSound);
	}

	/** The tone is read only after the Enable_Sound guard, exactly as each cue did before. */
	private void play(Player listener, Function<TurfSettings, TurfSettings.Tone> tone) {
		if (!settings.isCaptureSoundEnabled()) {
			return;
		}
		effect(tone.apply(settings)).playSound(listener);
	}
}
