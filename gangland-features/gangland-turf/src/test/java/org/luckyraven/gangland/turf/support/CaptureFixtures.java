package org.luckyraven.gangland.turf.support;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.turf.capture.CaptureSettings;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared Mockito-mock builders for {@code CaptureService} tests. Real {@link Gang} and {@link User} construction
 * needs static config binding that nothing in this test module runs (see {@link FakeGangLookup} /
 * {@link FakeUserLookup}), so every capture test drives the state machine with mocks of those two classes plus
 * plain {@link Player} mocks, registered into {@link FakeGangLookup} / {@link FakeUserLookup} by the test itself.
 */
public final class CaptureFixtures {

	private CaptureFixtures() {
	}

	public static Gang gang(int id) {
		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(id);
		return gang;
	}

	public static Player player() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.isDead()).thenReturn(false);
		return player;
	}

	public static Player deadPlayer() {
		Player player = player();
		when(player.isDead()).thenReturn(true);
		return player;
	}

	@SuppressWarnings("unchecked")
	public static User<Player> userInGang(int gangId) {
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(gangId);
		return user;
	}

	/**
	 * @param progressMilestones upward-crossing thresholds (e.g. {@code [25, 50, 75]})
	 */
	public static CaptureSettings settings(int captureDurationSeconds, int cooldownMinutes, int abandonGraceSeconds,
	                                       int postLogoffProtectionMinutes, int[] progressMilestones,
	                                       int unclaimedPhase1Seconds, int unclaimedPhase2Seconds) {
		return new CaptureSettings(captureDurationSeconds, cooldownMinutes, abandonGraceSeconds,
		                           postLogoffProtectionMinutes, 10, progressMilestones, true,
		                           unclaimedPhase1Seconds, unclaimedPhase2Seconds);
	}
}
