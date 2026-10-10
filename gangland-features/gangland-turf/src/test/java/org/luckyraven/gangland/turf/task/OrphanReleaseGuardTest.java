package org.luckyraven.gangland.turf.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * T-196: a gang table that failed to load arrives as an empty gang cache, which must not read as "every owner is
 * missing". With no gang loaded at all, the inactivity sweep and the income payout both skip; with any gang loaded, an
 * owner that is really missing is still released as ORPHANED.
 */
@DisplayName("Orphan release guard - an empty gang cache never frees owned turfs (T-196)")
class OrphanReleaseGuardTest {

	private static Turf ownedBy99(TurfManager turfs) {
		Turf turf = new Turf(1, "T", new CuboidRegion("world", 0, 0, 10, 10), 99, BigDecimal.TEN, 0L, 0L);
		turfs.create(turf);
		return turf;
	}

	private static TurfManager turfs() {
		return new TurfManager(mock(TurfRepositoryContract.class));
	}

	@Test
	@DisplayName("emptyGangCache_inactivitySkipsRelease: no gangs loaded, the sweep frees nothing")
	void emptyGangCache_inactivitySkipsRelease() {
		TurfManager turfs = turfs();
		Turf turf = ownedBy99(turfs);
		FakeGangLookup gangs = new FakeGangLookup();

		try (BukkitStatics ignored = BukkitStatics.install()) {
			new InactivityReleaseTask(turfs, gangs, new TurfOwnership(turfs), 7).run();
		}

		assertEquals(99, turf.getOwnerGangId());
	}

	@Test
	@DisplayName("emptyGangCache_incomeSkipsRelease: no gangs loaded, the payout frees nothing")
	void emptyGangCache_incomeSkipsRelease() {
		TurfManager turfs = turfs();
		Turf turf = ownedBy99(turfs);
		FakeGangLookup gangs = new FakeGangLookup();
		TurfIncomeDistributor distributor = new TurfIncomeDistributor(mock(JavaPlugin.class), turfs, gangs,
		                                                              mock(ActiveBuffManager.class),
		                                                              new TurfOwnership(turfs), 1200L);

		try (BukkitStatics ignored = BukkitStatics.install()) {
			distributor.distribute();
		}

		assertEquals(99, turf.getOwnerGangId());
	}

	@Test
	@DisplayName("otherGangLoaded_orphanStillReleased: an unrelated gang loaded, the missing owner is released")
	void otherGangLoaded_orphanStillReleased() {
		TurfManager turfs = turfs();
		Turf turf = ownedBy99(turfs);
		FakeGangLookup gangs = new FakeGangLookup();
		gangs.register(CaptureFixtures.gang(7));

		try (BukkitStatics ignored = BukkitStatics.install()) {
			new InactivityReleaseTask(turfs, gangs, new TurfOwnership(turfs), 7).run();
		}

		assertEquals(null, turf.getOwnerGangId());
	}

}
