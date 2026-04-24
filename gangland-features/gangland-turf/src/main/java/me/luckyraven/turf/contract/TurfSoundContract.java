package me.luckyraven.turf.contract;

import org.bukkit.entity.Player;

/**
 * Plays configured capture SFX. Implemented in gangland-impl using the plugin's {@code SoundConfiguration}
 * (XSound-backed) so the turf module does not import the impl-side sound infrastructure or raw
 * {@code org.bukkit.Sound}.
 */
public interface TurfSoundContract {

	void playCaptureStart(Player listener);

	void playCaptureComplete(Player listener);

	void playCaptureFailed(Player listener);

	/**
	 * Subtle "click" played on every upward bossbar tick so attackers get audio feedback that progress is moving.
	 * Implementations should keep the volume low to avoid spam at 1 Hz.
	 */
	void playCaptureTick(Player listener);

	/**
	 * Played to players inside a turf when its owning gang is cleared (admin {@code /glw turf setowner none},
	 * inactivity auto-release, etc.) — distinct from a failed capture so the audio cue makes the meaning obvious.
	 */
	void playOwnerCleared(Player listener);
}
