package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.HologramTrait;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopNames;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;
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

	private static final CopRole MEDIC = new CopRole("Medic", "Medic", NpcFanPlacement.CENTER, null, null, 1.0, null, 0,
	                                                 null, 1.0, 0, null, 0, 60, true, false);

	private static CopTierConfig tier(String displayName) {
		return new CopTierConfig(3, displayName, 30.0, 4.0, 1.2, 4.0, true, false, List.of(), List.of(), null, null,
		                         null, null, NpcDifficulty.NORMAL, TacticsConfig.DEFAULT, 0.1);
	}

	private static NPC npc(HologramTrait hologram) {
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(592);
		when(npc.getOrAddTrait(HologramTrait.class)).thenReturn(hologram);
		return npc;
	}

	@Test
	@DisplayName("the Citizens name is short and plain and the coloured callsign is one hologram line")
	void nameplate_shortNameHiddenPlateCallsignLine() {
		HologramTrait hologram = mock(HologramTrait.class);
		NPC           npc      = npc(hologram);

		String callsign = CopNpcFactory.CitizensBridge.nameplate(npc, new CopNames(CopNames.DEFAULT.format(),
		                                                                           List.of("Bob")), tier("&9Officer"),
		                                                         null, new Random(1));

		assertEquals("&9Officer &fBob &7#1592", callsign);

		ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
		verify(npc).setName(name.capture());
		assertEquals("Bob #1592", name.getValue());
		assertTrue(name.getValue().length() <= 16);
		assertFalse(name.getValue().contains("§"));

		verify(hologram).addLine(ChatColor.translateAlternateColorCodes('&', "&9Officer &fBob &7#1592"));
	}

	@Test
	@DisplayName("%rank% stays the tier's Display_Name in its colour for a cop with a role; the default Format ignores the role")
	void rank_staysTierDisplayName_withRole() {
		String callsign = CopNpcFactory.CitizensBridge.nameplate(npc(mock(HologramTrait.class)),
		                                                         new CopNames(CopNames.DEFAULT.format(),
		                                                                      List.of("Bob")), tier("&1SWAT"), MEDIC,
		                                                         new Random(1));

		assertEquals("&1SWAT &fBob &7#1592", callsign);
	}

	@Test
	@DisplayName("%role% is the role's display name on the hologram line; empty and collapsed for a cop with no role")
	void role_placeholder_onLineOrCollapsed() {
		CopNames      names    = new CopNames("%rank% &e%role% &f%name% &7#%badge%", List.of("Bob"));
		HologramTrait hologram = mock(HologramTrait.class);
		NPC           npc      = npc(hologram);

		assertEquals("&1SWAT &eMedic &fBob &7#1592",
		             CopNpcFactory.CitizensBridge.nameplate(npc, names, tier("&1SWAT"), MEDIC, new Random(1)));
		verify(hologram).addLine(ChatColor.translateAlternateColorCodes('&', "&1SWAT &eMedic &fBob &7#1592"));

		assertEquals("&1SWAT &fBob &7#1592",
		             CopNpcFactory.CitizensBridge.nameplate(npc, names, tier("&1SWAT"), null, new Random(1)));
	}
}
