package org.luckyraven.gangland.turf.task;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.log.TurfEventBuffer;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * The T-196 guard looks for an owned turf anywhere in the list, not just at its head: with no gang loaded, an unclaimed
 * turf in front of an owned one must not let the inactivity sweep or the income payout free the owned one.
 */
@DisplayName("Empty gang cache guard - an owned turf behind an unclaimed one still trips it (T-196)")
class OwnedTurfGuardTest {

	private static Turf turf(TurfManager turfs, int id, Integer owner) {
		Turf turf = new Turf(id, "T" + id, new CuboidRegion("world", id * 20, 0, id * 20 + 10, 10), owner,
		                     BigDecimal.TEN, 0L, 0L);
		turfs.create(turf);
		return turf;
	}

	private static TurfManager turfs() {
		return new TurfManager(mock(TurfRepositoryContract.class));
	}

	private static TurfIncomeDistributor distributor(TurfManager turfs, FakeGangLookup gangs) {
		return new TurfIncomeDistributor(mock(JavaPlugin.class), turfs, gangs, new FakeUserLookup(),
		                                 mock(ActiveBuffManager.class), new TurfOwnership(turfs), 1200L,
		                                 mock(TurfRules.class), new TurfEventBuffer(), mock(TurfMessages.class),
		                                 (player, line, priority) -> {
		                                 },
		                                 List::of, () -> 0L);
	}

	@Test
	@DisplayName("inactivity sweep: no gangs loaded, [unclaimed, owned by a missing gang] frees nothing")
	void inactivity_ownedTurfBehindAnUnclaimedOne_skips() {
		TurfManager turfs     = turfs();
		Turf        unclaimed = turf(turfs, 1, null);
		Turf        owned     = turf(turfs, 2, 99);

		try (BukkitStatics ignored = BukkitStatics.install()) {
			new InactivityReleaseTask(turfs, new FakeGangLookup(), new TurfOwnership(turfs), 7).run();
		}

		assertNull(unclaimed.getOwnerGangId());
		assertEquals(99, owned.getOwnerGangId());
	}

	@Test
	@DisplayName("income payout: no gangs loaded, [unclaimed, owned by a missing gang] frees nothing")
	void income_ownedTurfBehindAnUnclaimedOne_skips() {
		TurfManager turfs     = turfs();
		Turf        unclaimed = turf(turfs, 1, null);
		Turf        owned     = turf(turfs, 2, 99);

		try (BukkitStatics ignored = BukkitStatics.install()) {
			distributor(turfs, new FakeGangLookup()).distribute();
		}

		assertNull(unclaimed.getOwnerGangId());
		assertEquals(99, owned.getOwnerGangId());
	}

	@Test
	@DisplayName("no gangs loaded and no owned turf: both tasks run through and leave the unclaimed turfs alone")
	void noOwnedTurf_runsThrough() {
		TurfManager turfs     = turfs();
		Turf        unclaimed = turf(turfs, 1, null);

		try (BukkitStatics ignored = BukkitStatics.install()) {
			new InactivityReleaseTask(turfs, new FakeGangLookup(), new TurfOwnership(turfs), 7).run();
			distributor(turfs, new FakeGangLookup()).distribute();
		}

		assertNull(unclaimed.getOwnerGangId());
	}

	@Test
	@DisplayName("no gangs loaded and no turf at all: both tasks run through")
	void noTurfs_runsThrough() {
		TurfManager turfs = turfs();

		try (BukkitStatics ignored = BukkitStatics.install()) {
			new InactivityReleaseTask(turfs, new FakeGangLookup(), new TurfOwnership(turfs), 7).run();
			distributor(turfs, new FakeGangLookup()).distribute();
		}

		assertEquals(0, turfs.getAll().size());
	}
}
