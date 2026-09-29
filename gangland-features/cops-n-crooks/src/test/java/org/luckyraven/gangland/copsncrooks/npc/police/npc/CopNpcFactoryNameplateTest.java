package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.HologramTrait;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopNames;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CopNpcFactory nameplate - short Citizens name, hidden nameplate, coloured callsign line (phase H13)")
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
}
