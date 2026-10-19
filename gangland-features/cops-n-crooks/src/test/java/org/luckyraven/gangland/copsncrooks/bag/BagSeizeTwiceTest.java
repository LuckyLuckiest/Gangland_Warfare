package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.luckyraven.gangland.copsncrooks.bag.BagFixture.bd;

/** Judge-fix r1: a cuffed quit seizes the bag; the later jail-intake seize of the empty bag must not zero the line. */
@DisplayName("BagService.seize - a second seize of an empty bag keeps the first seized amount")
class BagSeizeTwiceTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("cuffed quit seizes 500, the intake seize returns 0 and the paperwork line still shows 500")
	void secondSeizeKeepsSeized() {
		BagFixture fx     = new BagFixture(tempDir);
		Player     runner = fx.player("Runner");
		fx.level(runner, 2);
		fx.service.take(runner, bd("500"), "pickup");
		fx.service.settle(runner);
		fx.restrained.add(runner.getUniqueId());

		fx.service.onQuit(runner);
		double again = fx.service.seize(runner);

		assertEquals(0.0, again);
		assertEquals(0, bd("500").compareTo(fx.bags.seized(runner.getUniqueId())));
		assertNotNull(fx.service.paperworkLine(runner.getUniqueId()));
	}
}
