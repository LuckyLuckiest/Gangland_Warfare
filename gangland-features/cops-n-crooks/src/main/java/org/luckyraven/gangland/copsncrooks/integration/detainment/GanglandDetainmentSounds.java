package org.luckyraven.gangland.copsncrooks.integration.detainment;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.luckyraven.keystone.sound.SoundEffect;

/**
 * Routes every detainment audio cue through the XSound-backed {@link SoundEffect}. Sound *names* come from
 * copsncrooks/detainment.yml ({@link DetainmentSettings}); the constants here are the volume / pitch envelopes tuned
 * for an audible, punchy response.
 */
public final class GanglandDetainmentSounds implements DetainmentSoundContract {

	// Volume of 2.0f makes the sound carry further — on Minecraft 1.20+ the clamp is 10, and since the release is
	// almost always within the player's own ear-shot this lands loud and clear.
	private static final float LOUD_VOLUME   = 2.0f;
	private static final float NORMAL_PITCH  = 1.0f;
	private static final float SUCCESS_PITCH = 1.25f;
	private static final float FAIL_PITCH    = 0.7f;

	private final DetainmentSettings settings;

	public GanglandDetainmentSounds(DetainmentSettings settings) {
		this.settings = settings;
	}

	@Override
	public void playBailSuccess(Player player) {
		play(player, settings.getBailSuccessSound(), LOUD_VOLUME, SUCCESS_PITCH);
	}

	@Override
	public void playBribeSuccess(Player player) {
		play(player, settings.getBribeSuccessSound(), LOUD_VOLUME, SUCCESS_PITCH);
	}

	@Override
	public void playBribeFail(Player player) {
		play(player, settings.getBribeFailSound(), LOUD_VOLUME, FAIL_PITCH);
	}

	@Override
	public void playTransitCommit(Player player) {
		play(player, settings.getTransitCommitSound(), LOUD_VOLUME, NORMAL_PITCH);
	}

	@Override
	public void playSentenceComplete(Player player) {
		play(player, settings.getSentenceCompleteSound(), LOUD_VOLUME, SUCCESS_PITCH);
	}

	@Override
	public void playBreakFreeSuccess(Player player) {
		play(player, settings.getBribeSuccessSound(), LOUD_VOLUME, SUCCESS_PITCH);
	}

	private void play(Player player, String soundName, float volume, float pitch) {
		if (player == null || soundName == null || soundName.isBlank()) return;
		new SoundEffect(SoundEffect.SoundType.VANILLA, soundName, volume, pitch).playSound(player);
	}
}
