package org.luckyraven.gangland.turf.stat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DisplayName("TurfsHeldStatContribution - the turfs ranking stat")
class TurfsHeldStatContributionTest {

	@Test
	@DisplayName("countsOwnedTurfs")
	void countsOwnedTurfs() {
		TurfManager turfs = new TurfManager(mock(TurfRepositoryContract.class));
		turfs.create(new Turf(1, "A", new CuboidRegion("w", 0, 0, 9, 9), 5, BigDecimal.TEN, 0L, 0L));
		turfs.create(new Turf(2, "B", new CuboidRegion("w", 20, 0, 29, 9), 5, BigDecimal.TEN, 0L, 0L));
		turfs.create(new Turf(3, "C", new CuboidRegion("w", 40, 0, 49, 9), 6, BigDecimal.TEN, 0L, 0L));
		TurfsHeldStatContribution stat = new TurfsHeldStatContribution(turfs);

		assertEquals("turfs", stat.key());
		assertEquals(2L, stat.value(5));
		assertEquals(0L, stat.value(9));
	}
}
