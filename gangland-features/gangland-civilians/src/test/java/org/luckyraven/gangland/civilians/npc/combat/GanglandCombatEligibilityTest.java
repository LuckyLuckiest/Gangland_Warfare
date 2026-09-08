package org.luckyraven.gangland.civilians.npc.combat;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link GanglandCombatEligibility#canBeHit}'s polarity (gangland-0.9.0.md T-H3, R1 B4): it is the
 * <strong>inverse</strong> of {@link DownedPlayerRegistry#isDowned}. Get this backwards and every downed player
 * becomes shootable by a Bartizan weapon with no test and no log line — the single riskiest inversion in this
 * stream's contract.
 */
@DisplayName("GanglandCombatEligibility.canBeHit — polarity (T-H3)")
class GanglandCombatEligibilityTest {

	private final GanglandCombatEligibility eligibility = new GanglandCombatEligibility();

	@Test
	@DisplayName("a downed player cannot be hit — canBeHit returns false")
	void downedPlayer_cannotBeHit() {
		UUID   uuid   = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);

		DownedPlayerRegistry.add(uuid);
		try {
			assertFalse(eligibility.canBeHit(player), "a downed player must never be hittable");
		} finally {
			DownedPlayerRegistry.remove(uuid);
		}
	}

	@Test
	@DisplayName("a player who is not downed can be hit — canBeHit returns true")
	void notDownedPlayer_canBeHit() {
		UUID   uuid   = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);

		// deliberately not added to DownedPlayerRegistry
		assertTrue(eligibility.canBeHit(player), "a player who isn't downed must stay hittable");
	}

}
