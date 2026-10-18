package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** P-LOOK-1: an unwanted gang mate relays; a wanted one, another gang, no gang module, or the hunted himself does not. */
@DisplayName("LookoutRelay")
class LookoutRelayTest {

	private final Map<UUID, Integer>   gangOf  = new HashMap<>();
	private final Map<Player, Integer> levels  = new HashMap<>();
	private final AtomicBoolean        enabled = new AtomicBoolean(true);

	private GangMembership gangs;
	private LookoutRelay   relay;
	private Player         hearer;
	private Player         hunted;

	@BeforeEach
	void setUp() {
		gangs = new GangMembership();
		GangMembershipView view = mock(GangMembershipView.class);
		when(view.gangIdOf(any(UUID.class))).thenAnswer(call -> gangOf.getOrDefault(call.<UUID>getArgument(0), -1));
		gangs.install(view);

		hearer = player();
		hunted = player();
		relay  = new LookoutRelay(enabled::get, p -> levels.getOrDefault(p, 0), gangs);
	}

	private Player player() {
		Player p = mock(Player.class);
		when(p.getUniqueId()).thenReturn(UUID.randomUUID());
		return p;
	}

	private void gang(Player p, int id) {
		gangOf.put(p.getUniqueId(), id);
	}

	@Test
	@DisplayName("a same-gang mate with no stars relays")
	void sameGangUnwantedRelays() {
		gang(hearer, 7);
		gang(hunted, 7);

		assertTrue(relay.test(hearer, hunted));
	}

	@Test
	@DisplayName("a mate who is wanted himself (level 1) does not relay")
	void wantedMateDoesNotRelay() {
		gang(hearer, 7);
		gang(hunted, 7);
		levels.put(hearer, 1);

		assertFalse(relay.test(hearer, hunted));
		levels.put(hearer, 0);
		assertTrue(relay.test(hearer, hunted));
	}

	@Test
	@DisplayName("no gang on both sides, or another gang, does not relay")
	void noGangOrOtherGangDoesNotRelay() {
		assertFalse(relay.test(hearer, hunted), "-1 / -1");

		gang(hearer, 7);
		gang(hunted, 8);
		assertFalse(relay.test(hearer, hunted), "7 / 8");

		gang(hunted, 7);
		assertTrue(relay.test(hearer, hunted), "7 / 7");
	}

	@Test
	@DisplayName("without the gang module (no view installed) nobody relays")
	void noGangModuleDoesNotRelay() {
		gang(hearer, 7);
		gang(hunted, 7);

		assertFalse(new LookoutRelay(enabled::get, p -> 0, new GangMembership()).test(hearer, hunted));
		assertTrue(relay.test(hearer, hunted));
	}

	@Test
	@DisplayName("the hunted player never relays to himself")
	void huntedNeverRelaysToHimself() {
		gang(hunted, 7);

		assertFalse(relay.test(hunted, hunted));
		gang(hearer, 7);
		assertTrue(relay.test(hearer, hunted));
	}

	@Test
	@DisplayName("Cops.Lookout.Enable false silences the relay, read on every call")
	void disabledNeverRelays() {
		gang(hearer, 7);
		gang(hunted, 7);

		enabled.set(false);
		assertFalse(relay.test(hearer, hunted));
		enabled.set(true);
		assertTrue(relay.test(hearer, hunted));
	}
}
