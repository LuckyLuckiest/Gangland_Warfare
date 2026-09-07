package org.luckyraven.gangland.copsncrooks.detainment.sound;

import org.bukkit.entity.Player;

/**
 * Emits the audio cues that accompany detainment events. Implementations route each call through the host's
 * {@code SoundEffect} framework so cops-n-crooks code doesn't touch the XSound API directly.
 */
public interface DetainmentSoundContract {

	void playBailSuccess(Player player);

	void playBribeSuccess(Player player);

	void playBribeFail(Player player);

	void playTransitCommit(Player player);

	void playSentenceComplete(Player player);

	void playBreakFreeSuccess(Player player);
}
