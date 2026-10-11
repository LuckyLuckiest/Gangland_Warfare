package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the TF-49 predicate: a kill counts as defending your own turf only when the killer's gang owns the turf being
 * contested at the kill location.
 */
@DisplayName("DefendingOwnTurf")
class DefendingOwnTurfTest {

	private static final int OWNER   = 3;
	private static final int TURF_ID = 7;

	private final UUID uuid = UUID.randomUUID();

	private TurfManager        turfs;
	private Turf               turf;
	private TurfRuntimeState   state;
	private Player             killer;
	private Location           at;
	private int                killerGang;
	private DefendingOwnTurf   predicate;

	@BeforeEach
	void setUp() {
		turfs = mock(TurfManager.class);
		turf  = mock(Turf.class);
		state = mock(TurfRuntimeState.class);
		killer = mock(Player.class);
		at     = mock(Location.class);

		killerGang = OWNER;
		GangMembership gangs = new GangMembership();
		gangs.install(new GangMembershipView() {
			@Override
			public int gangIdOf(UUID id) {
				return id.equals(uuid) ? killerGang : -1;
			}

			@Override
			public boolean gangsAllied(int gangIdA, int gangIdB) {
				return false;
			}

			@Override
			public Optional<String> nameOf(int gangId) {
				return Optional.empty();
			}
		});

		when(killer.getUniqueId()).thenReturn(uuid);
		when(turfs.findAt(at)).thenReturn(turf);
		when(turf.getId()).thenReturn(TURF_ID);
		when(turfs.getRuntimeState(TURF_ID)).thenReturn(state);
		when(state.getState()).thenReturn(TurfState.CONTESTING);
		when(turf.getOwnerGangId()).thenReturn(OWNER);

		predicate = new DefendingOwnTurf(turfs, gangs);
	}

	@Test
	@DisplayName("owner's gang killing inside its own contested turf is a defence")
	void defenderInsideOwnContestedTurf_true() {
		assertTrue(predicate.test(killer, at));
	}

	@Test
	@DisplayName("an attacker from another gang is not a defence")
	void attacker_false() {
		killerGang = 5;
		assertFalse(predicate.test(killer, at));
	}

	@Test
	@DisplayName("a kill outside any turf is not a defence")
	void outsideAnyTurf_false() {
		when(turfs.findAt(at)).thenReturn(null);
		assertFalse(predicate.test(killer, at));
	}

	@Test
	@DisplayName("own turf that is not contested is not a defence")
	void ownTurfNotContested_false() {
		when(state.getState()).thenReturn(TurfState.IDLE);
		assertFalse(predicate.test(killer, at));

		when(state.getState()).thenReturn(TurfState.COOLDOWN);
		assertFalse(predicate.test(killer, at));

		when(turfs.getRuntimeState(TURF_ID)).thenReturn(null);
		assertFalse(predicate.test(killer, at));
	}

	@Test
	@DisplayName("a killer in no gang is not a defence")
	void ganglessKiller_false() {
		killerGang = -1;
		assertFalse(predicate.test(killer, at));
	}

	@Test
	@DisplayName("an unclaimed turf is not a defence")
	void unclaimedTurf_false() {
		when(turf.getOwnerGangId()).thenReturn(null);
		assertFalse(predicate.test(killer, at));
	}
}
