package org.luckyraven.gangland.listener.gang;

import org.junit.jupiter.api.DisplayName;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.events.gang.GangLevelUpEvent;
import org.luckyraven.gangland.core.user.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test
	@DisplayName("T-926: the message runs at MONITOR and skips a cancelled level-up, like the reward listener")
	void levelUpMessage_isNotSentForACancelledLevelUp() throws NoSuchMethodException {
		EventHandler handler = GangLevelMessageListener.class.getMethod("onGangLevelUp", GangLevelUpEvent.class)
		                                                     .getAnnotation(EventHandler.class);

		assertEquals(EventPriority.MONITOR, handler.priority(), "must see the cancel decided at every earlier priority");
		assertTrue(handler.ignoreCancelled(), "a cancelled level-up must not announce a level the gang never reached");
	}

}
