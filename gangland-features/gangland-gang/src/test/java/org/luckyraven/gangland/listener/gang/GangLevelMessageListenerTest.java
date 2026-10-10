package org.luckyraven.gangland.listener.gang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GangManager fires GangLevelUpEvent after the level went up, so the listener must read the new level: a gang that
 * just reached level 1 is "leveled up to level 1", never level 2.
 */
@DisplayName("GangLevelMessageListener - level-up text names the level just reached")
class GangLevelMessageListenerTest {

	@Test
	@DisplayName("a Level at levelValue 1 produces 'leveled up to level 1' (not 2)")
	void levelUpText_namesTheLevelReached() {
		Level level = new Level(4, 200D);
		level.setLevelValue(1);

		String text = GangLevelMessageListener.replacePlaceholders("leveled up to level %next_level%", level);

		assertEquals("leveled up to level 1", text);
	}

	@Test
	@DisplayName("%level% is the level before the step, %next_level% the level reached")
	void levelPlaceholder_isTheLevelReached() {
		Level level = new Level(4, 200D);
		level.setLevelValue(2);

		String text = GangLevelMessageListener.replacePlaceholders("%level%/%next_level% of %max_level%", level);

		assertEquals("1/2 of 4", text);
	}

}

