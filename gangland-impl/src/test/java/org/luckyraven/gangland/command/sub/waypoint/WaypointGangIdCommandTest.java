package org.luckyraven.gangland.command.sub.waypoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link WaypointGangIdCommand#gangUnknown(GangMembership, int)} — the restored
 * {@code gang == null -> GANG_DOESNT_EXIST} guard (W54 second re-review) for the rare desync window where a
 * player's cached {@code User.gangId} still names a gang that stopped existing. The behaviour that matters most
 * is the "no module installed" case staying {@code false}: an uninstalled module can't distinguish "no gang"
 * from "don't know," so the raw-id path (this command's fallback everywhere else) must be trusted instead of
 * rejecting a value the command can't actually verify.
 */
@DisplayName("WaypointGangIdCommand.gangUnknown - the restored gang-exists guard")
class WaypointGangIdCommandTest {

	@Test
	@DisplayName("no module installed: never unknown, even for an id no one could vouch for")
	void gangUnknown_noModuleInstalled_isFalse() {
		GangMembership membership = new GangMembership();

		assertFalse(WaypointGangIdCommand.gangUnknown(membership, 7),
		            "an uninstalled module can't tell \"no gang\" from \"don't know\" - must not reject");
	}

	@Test
	@DisplayName("module installed, id names a real gang: not unknown")
	void gangUnknown_installedAndNameKnown_isFalse() {
		GangMembership membership = new GangMembership();
		membership.install(fixedView(7, "The Syndicate"));

		assertFalse(WaypointGangIdCommand.gangUnknown(membership, 7));
	}

	@Test
	@DisplayName("module installed, id names no real gang: unknown")
	void gangUnknown_installedAndNameMissing_isTrue() {
		GangMembership membership = new GangMembership();
		membership.install(fixedView(7, "The Syndicate"));

		assertTrue(WaypointGangIdCommand.gangUnknown(membership, 8),
		           "id 8 has no entry in the installed view - this is exactly the deleted-gang desync case");
	}

	private GangMembershipView fixedView(int knownId, String knownName) {
		return new GangMembershipView() {
			@Override
			public int gangIdOf(UUID uuid) {
				return -1;
			}

			@Override
			public boolean gangsAllied(int gangIdA, int gangIdB) {
				return false;
			}

			@Override
			public Optional<String> nameOf(int gangId) {
				return gangId == knownId ? Optional.of(knownName) : Optional.empty();
			}
		};
	}

}
