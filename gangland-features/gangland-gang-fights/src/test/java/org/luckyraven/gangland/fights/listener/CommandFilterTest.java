package org.luckyraven.gangland.fights.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@DisplayName("FightCommandListener - namespace-proof command filter during a fight (GP-31)")
class CommandFilterTest {

	private FightRig             rig;
	private FightCommandListener listener;

	@BeforeEach
	void setUp() {
		rig = new FightRig();
		Map<String, String> aliases = Map.of("teleport", "tp", "tell", "msg", "w", "msg", "gangland", "glw");
		listener = new FightCommandListener(rig.manager, rig.rules, rig.messages) {
			@Override
			String canonical(String token) {
				String lower = token.toLowerCase(Locale.ROOT);
				return aliases.getOrDefault(lower, lower);
			}
		};
		rig.running(0);
	}

	private PlayerCommandPreprocessEvent run(String message) {
		PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(rig.a, message);
		listener.onCommand(event);
		return event;
	}

	@Test
	@DisplayName("it runs at HIGHEST so it sees what other plugins rewrote")
	void priority_isHighest() throws Exception {
		EventHandler handler = FightCommandListener.class.getMethod("onCommand", PlayerCommandPreprocessEvent.class)
		                                               .getAnnotation(EventHandler.class);
		assertEquals(EventPriority.HIGHEST, handler.priority());
	}

	@Test
	@DisplayName("/minecraft:tp, /MINECRAFT:TP and /bukkit:teleport are all blocked like /tp")
	void namespaced_MINECRAFT_TP_blocked() {
		assertTrue(run("/tp 0 0 0").isCancelled());
		assertTrue(run("/minecraft:tp Steve").isCancelled());
		assertTrue(run("/MINECRAFT:TP Steve").isCancelled());
		assertTrue(run("/bukkit:teleport Steve").isCancelled());
	}

	@Test
	@DisplayName("a command that is only an alias of an unlisted one is blocked, not let around the list")
	void alias_blocked() {
		assertTrue(run("/teleport Steve").isCancelled());
		assertTrue(run("/home").isCancelled());
	}

	@Test
	@DisplayName("the allowlist lets the fight verbs and chat through, by whole word, alias and namespace included")
	void allowlist_passes() {
		assertFalse(run("/glw gang fight info").isCancelled());
		assertFalse(run("/gangland gang fight drop").isCancelled());
		assertFalse(run("/minecraft:msg Steve hi").isCancelled());
		assertFalse(run("/tell Steve hi").isCancelled());
		assertTrue(run("/glw gang fighter").isCancelled(), "an allowed entry matches whole words only");
		assertTrue(run("/glw gang home").isCancelled());
		assertTrue(run("/msgx Steve").isCancelled());
	}

	@Test
	@DisplayName("the bypass node skips the filter")
	void bypassNode_passes() {
		when(rig.a.hasPermission("gangland.gang.fight.bypass_commands")).thenReturn(true);

		assertFalse(run("/tp 0 0 0").isCancelled());
	}

	@Test
	@DisplayName("BLOCKLIST blocks only the listed commands and OFF blocks nothing")
	void otherModes() {
		when(rig.rules.getCommandBlockMode()).thenReturn("BLOCKLIST");
		assertTrue(run("/minecraft:tp x").isCancelled());
		assertFalse(run("/home").isCancelled());

		when(rig.rules.getCommandBlockMode()).thenReturn("OFF");
		assertFalse(run("/tp x").isCancelled());
	}

	@Test
	@DisplayName("a player who is not in a fight, or already knocked out, is never filtered")
	void nonFighters_passes() {
		PlayerCommandPreprocessEvent outsider = new PlayerCommandPreprocessEvent(rig.player(), "/tp x");
		listener.onCommand(outsider);
		assertFalse(outsider.isCancelled());

		rig.manager.fightOf(rig.a.getUniqueId()).orElseThrow().eliminate(rig.a.getUniqueId());
		assertFalse(run("/tp x").isCancelled());
	}
}
