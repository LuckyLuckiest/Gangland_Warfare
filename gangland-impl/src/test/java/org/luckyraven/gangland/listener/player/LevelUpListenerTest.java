package org.luckyraven.gangland.listener.player;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("LevelUpListener - the level-up message names the level reached")
class LevelUpListenerTest {

	@Test
	@DisplayName("T-253: %level% is the level before, %next_level% the level just reached, %max_level% the cap")
	void placeholders_nameTheLevelReached() {
		Level level = new Level(10, 1000);
		level.setLevelValue(3);

		String message = LevelUpListener.replacePlaceholders("from %level% to %next_level% of %max_level%", level);

		assertEquals("from 2 to 3 of 10", message);
	}

}
