package org.luckyraven.gangland.turf.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DisplayName("TurfManager - turfs in a gang-disabled world are reported once at load")
class TurfManagerGangWorldTest {

	@Test
	@DisplayName("oneEntryPerTurfInDisabledWorld")
	void oneEntryPerTurfInDisabledWorld() {
		TurfManager turfs = new TurfManager(mock(TurfRepositoryContract.class));
		turfs.create(new Turf(1, "A", new CuboidRegion("off", 0, 0, 9, 9), null, BigDecimal.TEN, 0L, 0L));
		turfs.create(new Turf(2, "B", new CuboidRegion("off", 20, 0, 29, 9), null, BigDecimal.TEN, 0L, 0L));
		turfs.create(new Turf(3, "C", new CuboidRegion("on", 0, 0, 9, 9), null, BigDecimal.TEN, 0L, 0L));
		turfs.setGangsDisabledIn("off"::equals);

		assertEquals(2, turfs.warnGangDisabledWorlds().size());
	}

	@Test
	@DisplayName("noPolicy_noWarnings")
	void noPolicy_noWarnings() {
		TurfManager turfs = new TurfManager(mock(TurfRepositoryContract.class));
		turfs.create(new Turf(1, "A", new CuboidRegion("off", 0, 0, 9, 9), null, BigDecimal.TEN, 0L, 0L));

		assertEquals(0, turfs.warnGangDisabledWorlds().size());
	}
}
