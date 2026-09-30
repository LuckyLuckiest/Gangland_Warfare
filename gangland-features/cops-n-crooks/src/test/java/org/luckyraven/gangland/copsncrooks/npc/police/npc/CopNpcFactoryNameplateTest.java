package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.HologramTrait;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopNames;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CopNpcFactory nameplate - short Citizens name, coloured callsign line (phase H13)")
class CopNpcFactoryNameplateTest {

	@Test
	@DisplayName("the Citizens name is short and plain and the coloured callsign is one hologram line")
	void nameplate_shortNameHiddenPlateCallsignLine() {
		NPC           npc      = mock(NPC.class);
		HologramTrait hologram = mock(HologramTrait.class);
		when(npc.getId()).thenReturn(592);
		when(npc.getOrAddTrait(HologramTrait.class)).thenReturn(hologram);

		String callsign = CopNpcFactory.CitizensBridge.nameplate(npc, new CopNames(CopNames.DEFAULT.format(),
		                                                                           List.of("Bob")), "&9Officer",
		                                                         new Random(1));

		assertEquals("&9Officer &fBob &7#1592", callsign);

		ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
		verify(npc).setName(name.capture());
		assertEquals("Bob #1592", name.getValue());
		assertTrue(name.getValue().length() <= 16);
		assertFalse(name.getValue().contains("§"));

		verify(hologram).addLine(ChatColor.translateAlternateColorCodes('&', "&9Officer &fBob &7#1592"));
	}

	@Test
	@DisplayName("%rank% is the role's display name in the tier's colour; the tier's Display_Name without a role")
	void rank_roleDisplayNameInTierColour() {
		CopRole medic = new CopRole("Medic", "Medic", NpcFanPlacement.CENTER, null, null, 1.0, null, 0, null, 1.0, 0,
		                            null, 0, 60, true, false);

		assertEquals("§9Medic", CopNpcFactory.rank("&9Officer", medic));
		assertEquals("&9Officer", CopNpcFactory.rank("&9Officer", null));

		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(592);
		when(npc.getOrAddTrait(HologramTrait.class)).thenReturn(mock(HologramTrait.class));
		String callsign = CopNpcFactory.CitizensBridge.nameplate(npc, new CopNames(CopNames.DEFAULT.format(),
		                                                                           List.of("Bob")),
		                                                         CopNpcFactory.rank("&1SWAT", medic), new Random(1));
		assertEquals("§1Medic &fBob &7#1592", callsign);
	}
}
