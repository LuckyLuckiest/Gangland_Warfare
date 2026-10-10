package org.luckyraven.gangland.gang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.Level;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D2 pacing: the default level curve (base 200, level n needs base x n^1.5 more XP) reached at one turf paying 144 XP
 * a day. Level 1 falls on day 2, level 2 on day 6, level 3 on day 13 (1-based day index).
 */
@DisplayName("GangLevels - the default curve paces level 1 to 3 at one paying turf")
class GangLevelsTest {

	@Test
	@DisplayName("daysToLevel: level 1 on day 2, level 2 on day 6, level 3 on day 13")
	void daysToLevel() {
		Level level = GangLevels.newLevel();
		List<Integer> reachedOnDay = new ArrayList<>();

		for (int day = 1; reachedOnDay.size() < 3 && day <= 60; day++) {
			level.addExperience(144, true, null);
			while (reachedOnDay.size() < level.getLevelValue()) {
				reachedOnDay.add(day);
			}
		}

		assertEquals(List.of(2, 6, 13), reachedOnDay.subList(0, 3));
	}

}
