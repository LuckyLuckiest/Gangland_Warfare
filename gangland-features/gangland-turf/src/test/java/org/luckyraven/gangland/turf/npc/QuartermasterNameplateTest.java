package org.luckyraven.gangland.turf.npc;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuartermasterNameplateTest {

	private TurfManager            turfs;
	private GangLookupContract     gangs;
	private TurfPowerupManager     npcs;
	private TurfMessages           messages;
	private String                 template = "&8[&e%tag%&8]";
	private QuartermasterNameplate plate;

	@BeforeEach
	void setUp() {
		turfs    = mock(TurfManager.class);
		gangs    = mock(GangLookupContract.class);
		npcs     = mock(TurfPowerupManager.class);
		messages = mock(TurfMessages.class);
		when(messages.text(anyString(), anyString(), any(Object[].class))).thenAnswer(inv -> {
			String value = template;
			// varargs arrive flattened after the two fixed arguments
			Object[] args = inv.getArguments();
			for (int i = 2; i + 1 < args.length; i += 2) {
				value = value.replace("%" + args[i] + "%", String.valueOf(args[i + 1]));
			}
			return value.replace('&', ChatColor.COLOR_CHAR);
		});
		plate = new QuartermasterNameplate(turfs, gangs, npcs, messages);
	}

	private void owned(int turfId, int gangId, String name) {
		Turf turf = mock(Turf.class);
		when(turf.getOwnerGangId()).thenReturn(gangId);
		when(turfs.get(turfId)).thenReturn(turf);
		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(gangId);
		when(gang.getDisplayNameString()).thenReturn(name);
		when(gangs.findById(gangId)).thenReturn(gang);
	}

	@Test
	void lineFor_missingUnownedOrGangless() {
		assertEquals("", plate.lineFor(9));
		Turf unowned = mock(Turf.class);
		when(unowned.getOwnerGangId()).thenReturn(null);
		when(turfs.get(1)).thenReturn(unowned);
		assertEquals("", plate.lineFor(1));
		Turf ownedTurf = mock(Turf.class);
		when(ownedTurf.getOwnerGangId()).thenReturn(5);
		when(turfs.get(2)).thenReturn(ownedTurf);
		assertEquals("", plate.lineFor(2));
	}

	@Test
	void lineFor_namesTag() {
		owned(1, 1, "Vipers");
		assertEquals("[VIP]", ChatColor.stripColor(plate.lineFor(1)));
	}

	@Test
	void lineFor_longTemplateFallsBackToBrackets() {
		template = "&8[&e%tag%&8] Owned by the gang";
		owned(1, 1, "Vipers");
		assertEquals("[VIP]", ChatColor.stripColor(plate.lineFor(1)));
	}

	@Test
	void lineFor_longNameStaysShort() {
		owned(1, 1, "Abcdefghijklmnopqrstuvwxyzabcdefghijklmn");
		assertTrue(ChatColor.stripColor(plate.lineFor(1)).length() <= 16);
	}

	@Test
	void registersSpawnHook() {
		verify(npcs).setSpawnHook(any());
	}

	@Test
	void refresh_skipsMissingOrDeadNpc() {
		assertDoesNotThrow(() -> plate.refresh(1));
		TurfPowerupNpc dead = mock(TurfPowerupNpc.class);
		when(dead.isAlive()).thenReturn(false);
		when(npcs.get(2)).thenReturn(dead);
		assertDoesNotThrow(() -> plate.refresh(2));
	}
}
