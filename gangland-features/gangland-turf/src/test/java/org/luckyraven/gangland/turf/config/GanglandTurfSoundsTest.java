package org.luckyraven.gangland.turf.config;

import com.cryptomorin.xseries.XSound;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.config.TurfSettings.Tone;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.function.BiConsumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link GanglandTurfSounds}: each capture cue plays its own configured tone through a {@code SoundEffect}, and the
 * Enable_Sound switch is read first, before any tone is read or any effect is built. Every tone name here is a
 * made-up one, so {@code SoundEffect}'s XSound lookup ({@code XSound.of(name)}) is the one observable call per cue.
 */
@DisplayName("GanglandTurfSounds - one configured tone per cue, gated by Enable_Sound")
class GanglandTurfSoundsTest {

	private static final Tone START     = new Tone("START_SOUND", 0.1F, 1.1F);
	private static final Tone COMPLETE  = new Tone("COMPLETE_SOUND", 0.2F, 1.2F);
	private static final Tone FAILED    = new Tone("FAILED_SOUND", 0.3F, 1.3F);
	private static final Tone TICK      = new Tone("TICK_SOUND", 0.4F, 1.4F);
	private static final Tone UNCLAIMED = new Tone("UNCLAIMED_SOUND", 0.5F, 1.5F);

	private TurfSettings         settings;
	private GanglandTurfSounds   sounds;
	private Player               listener;
	private MockedStatic<XSound> xsound;

	@BeforeEach
	void setUp() {
		settings = mock(TurfSettings.class);
		when(settings.getStartSound()).thenReturn(START);
		when(settings.getCompleteSound()).thenReturn(COMPLETE);
		when(settings.getFailedSound()).thenReturn(FAILED);
		when(settings.getTickSound()).thenReturn(TICK);
		when(settings.getUnclaimedSound()).thenReturn(UNCLAIMED);
		sounds   = new GanglandTurfSounds(settings);
		listener = mock(Player.class);

		xsound = mockStatic(XSound.class);
		xsound.when(() -> XSound.of(anyString())).thenReturn(Optional.empty());
	}

	@AfterEach
	void tearDown() {
		xsound.close();
	}

	/** Exactly one XSound lookup happened, and it was for this tone's name. */
	private void assertPlayed(Tone tone) {
		xsound.verify(() -> XSound.of(anyString()), times(1));
		xsound.verify(() -> XSound.of(tone.name()), times(1));
	}

	private void assertSilent(BiConsumer<GanglandTurfSounds, Player> cue) {
		when(settings.isCaptureSoundEnabled()).thenReturn(false);
		clearInvocations(settings);

		cue.accept(sounds, listener);

		xsound.verify(() -> XSound.of(anyString()), never());
		InOrder order = inOrder(settings);
		order.verify(settings).isCaptureSoundEnabled();
		verifyNoMoreInteractions(settings);
	}

	@Test
	@DisplayName("playCaptureStart plays the start tone")
	void captureStart() {
		when(settings.isCaptureSoundEnabled()).thenReturn(true);

		sounds.playCaptureStart(listener);

		assertPlayed(START);
		InOrder order = inOrder(settings);
		order.verify(settings).isCaptureSoundEnabled();
		order.verify(settings).getStartSound();
		verifyNoMoreInteractions(settings);
	}

	@Test
	@DisplayName("playCaptureComplete plays the complete tone")
	void captureComplete() {
		when(settings.isCaptureSoundEnabled()).thenReturn(true);

		sounds.playCaptureComplete(listener);

		assertPlayed(COMPLETE);
		InOrder order = inOrder(settings);
		order.verify(settings).isCaptureSoundEnabled();
		order.verify(settings).getCompleteSound();
		verifyNoMoreInteractions(settings);
	}

	@Test
	@DisplayName("playCaptureFailed plays the failed tone")
	void captureFailed() {
		when(settings.isCaptureSoundEnabled()).thenReturn(true);

		sounds.playCaptureFailed(listener);

		assertPlayed(FAILED);
		InOrder order = inOrder(settings);
		order.verify(settings).isCaptureSoundEnabled();
		order.verify(settings).getFailedSound();
		verifyNoMoreInteractions(settings);
	}

	@Test
	@DisplayName("playCaptureTick plays the tick tone")
	void captureTick() {
		when(settings.isCaptureSoundEnabled()).thenReturn(true);

		sounds.playCaptureTick(listener);

		assertPlayed(TICK);
		InOrder order = inOrder(settings);
		order.verify(settings).isCaptureSoundEnabled();
		order.verify(settings).getTickSound();
		verifyNoMoreInteractions(settings);
	}

	@Test
	@DisplayName("playOwnerCleared plays the unclaimed tone")
	void ownerCleared() {
		when(settings.isCaptureSoundEnabled()).thenReturn(true);

		sounds.playOwnerCleared(listener);

		assertPlayed(UNCLAIMED);
		InOrder order = inOrder(settings);
		order.verify(settings).isCaptureSoundEnabled();
		order.verify(settings).getUnclaimedSound();
		verifyNoMoreInteractions(settings);
	}

	@Test
	@DisplayName("with Enable_Sound off no cue reads a tone or looks a sound up")
	void disabledIsSilent() {
		assertSilent(GanglandTurfSounds::playCaptureStart);
		assertSilent(GanglandTurfSounds::playCaptureComplete);
		assertSilent(GanglandTurfSounds::playCaptureFailed);
		assertSilent(GanglandTurfSounds::playCaptureTick);
		assertSilent(GanglandTurfSounds::playOwnerCleared);
	}
}
