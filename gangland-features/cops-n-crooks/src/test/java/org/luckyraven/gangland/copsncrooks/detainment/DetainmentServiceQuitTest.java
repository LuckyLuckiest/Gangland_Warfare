package org.luckyraven.gangland.copsncrooks.detainment;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.gangland.copsncrooks.support.FakeRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the {@code CJ-04} fix (issue #34, cops-detainment-jail.md Observation #4): quitting while HANDCUFFED used to
 * write {@code DetainmentState.JAILED} straight into the registry, bypassing {@code JailIntakeService} — so the
 * player came back JAILED with a {@code null sentenceExpiresAt}, which makes {@code SentenceService.tick} return
 * early and never releases them.
 *
 * <p>After the fix {@code handleQuit} leaves the state HANDCUFFED and marks the transit timer due, so the
 * {@code PlayerJoinEvent} handler's {@code TransitService.resumeOnJoin} commits the player through the real intake
 * pipeline (cell occupancy, inventory seizure, wanted clear, paperwork, sentence expiry) on their next login.
 *
 * <p>No Bukkit statics are touched: {@code handleQuit} only reads/writes the registry.
 */
@DisplayName("DetainmentService.handleQuit — quitting while handcuffed does not fake a jailing")
class DetainmentServiceQuitTest {

	private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

	private FakeRepository<DetainedPlayer> repository;
	private DetainmentRegistry             registry;
	private DetainmentService              service;
	private Player                         player;

	@BeforeEach
	void setUp() {
		repository = new FakeRepository<>();
		registry   = new DetainmentRegistry(repository, new JailRegistry());

		service = new DetainmentService(mock(JavaPlugin.class), registry,
		                                mock(JailService.class), new JailRegistry(),
		                                mock(DetainmentMessageContract.class), "gangland");

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(PLAYER_ID);
	}

	@Test
	@DisplayName("a handcuffed quitter stays HANDCUFFED instead of being promoted to JAILED")
	void handleQuit_handcuffed_doesNotPromoteToJailed() {
		registry.setState(PLAYER_ID, DetainmentState.HANDCUFFED);

		service.handleQuit(player);

		assertEquals(DetainmentState.HANDCUFFED, registry.getState(PLAYER_ID),
		             "quitting must not fabricate a JAILED row that skipped JailIntakeService");
	}

	@Test
	@DisplayName("a handcuffed quitter gets a due transit expiry so the rejoin commits through intake")
	void handleQuit_handcuffed_marksTransitDue() {
		registry.setState(PLAYER_ID, DetainmentState.HANDCUFFED);
		long before = System.currentTimeMillis();

		service.handleQuit(player);

		DetainedPlayer detained = registry.getDetainedPlayers().get(PLAYER_ID);
		assertNotNull(detained);
		assertNotNull(detained.getTransitExpiresAt(),
		              "TransitService.resumeOnJoin returns early on a null expiry, so the player would never commit");
		assertTrue(detained.getTransitExpiresAt() <= System.currentTimeMillis(),
		           "the expiry must already be due so the commit fires on the next tick after rejoin");
		assertTrue(detained.getTransitExpiresAt() >= before);
	}

	@Test
	@DisplayName("no sentence expiry is invented on quit — that stays JailIntakeService's job")
	void handleQuit_handcuffed_leavesSentenceExpiryToIntake() {
		registry.setState(PLAYER_ID, DetainmentState.HANDCUFFED);

		service.handleQuit(player);

		assertNull(registry.getDetainedPlayers().get(PLAYER_ID).getSentenceExpiresAt());
	}

	@Test
	@DisplayName("the quit write is persisted immediately, not left to the next auto-save")
	void handleQuit_handcuffed_persistsTheRow() {
		registry.setState(PLAYER_ID, DetainmentState.HANDCUFFED);
		int savesAfterCuff = repository.saved.size();

		service.handleQuit(player);

		assertTrue(repository.saved.size() > savesAfterCuff, "handleQuit must save the mutated DetainedPlayer");
	}

	@Test
	@DisplayName("a player who is not handcuffed is untouched by quit")
	void handleQuit_notHandcuffed_isNoOp() {
		registry.setState(PLAYER_ID, DetainmentState.JAILED);
		int savesAfterJail = repository.saved.size();

		service.handleQuit(player);

		assertEquals(DetainmentState.JAILED, registry.getState(PLAYER_ID));
		assertNull(registry.getDetainedPlayers().get(PLAYER_ID).getTransitExpiresAt());
		assertEquals(savesAfterJail, repository.saved.size());
	}
}
