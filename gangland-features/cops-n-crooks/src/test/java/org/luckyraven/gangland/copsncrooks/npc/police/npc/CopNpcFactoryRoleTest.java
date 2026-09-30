package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import com.cryptomorin.xseries.XAttribute;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("CopNpcFactory roles - off-hand shield on a PLAYER entity, fan placement, leader priority, reach-clamped band")
class CopNpcFactoryRoleTest {

	private static final CopRole DEFENDER = new CopRole("Defender", "Defender", NpcFanPlacement.CENTER, 4.0, 7.0, 1.5,
	                                                    new ItemStack(Material.SHIELD), 0, 0.0, 1.0, 0, null, 0.5, 60,
	                                                    false, false);
	private static final CopRole MARKSMAN = new CopRole("Marksman", "Marksman", NpcFanPlacement.ANY, 14.0, 22.0, 1.0,
	                                                    null, 0, null, 0.6, 1, null, 0, 60, false, false);
	private static final CopRole COMMANDER = new CopRole("Commander", "Commander", NpcFanPlacement.ANY, null, null,
	                                                     1.0, null, 2, null, 1.0, 0, null, 0, 60, false, true);

	@Test
	@DisplayName("the loadout puts the role's off-hand item on a PLAYER entity and never touches a drop chance (CraftInventoryPlayer throws)")
	void loadout_offHandOnPlayer_noDropChance() {
		NPC             npc       = mock(NPC.class, RETURNS_DEEP_STUBS);
		Player          entity    = mock(Player.class);
		EntityEquipment equipment = mock(EntityEquipment.class);
		when(entity.getEquipment()).thenReturn(equipment);
		when(entity.getAttribute(XAttribute.MAX_HEALTH.get())).thenReturn(mock(AttributeInstance.class));
		when(npc.isSpawned()).thenReturn(true);
		when(npc.getEntity()).thenReturn(entity);
		CopTierConfig tier = tier();
		CopNpc cop = new CopNpc(mock(JavaPlugin.class), npc, tier, Map.of(), new Location(null, 0, 0, 0),
		                        mock(CopConfigProvider.class));
		cop.setRole(DEFENDER);

		cop.setLoadout(CopNpcFactory.loadout(cop, tier, mock(NpcMarkManager.class), null));

		ArgumentCaptor<ItemStack> offHand = ArgumentCaptor.forClass(ItemStack.class);
		verify(equipment).setItemInOffHand(offHand.capture());
		assertEquals(Material.SHIELD, offHand.getValue().getType());
		verify(equipment, never()).setItemInOffHandDropChance(anyFloat());
		verify(equipment, never()).setItemInMainHandDropChance(anyFloat());
	}

	@Test
	@DisplayName("applyRole sets placement and leader priority, and the band clamped under the weapon's reach")
	void applyRole_placementPriorityAndClampedBand() {
		CopNpc defender = mock(CopNpc.class);
		CopNpc marksman = mock(CopNpc.class);

		CopNpcFactory.applyRole(defender, DEFENDER, 30.0);
		CopNpcFactory.applyRole(marksman, MARKSMAN, 10.0); // the live rifle: Distance 10

		verify(defender).setFanPlacement(NpcFanPlacement.CENTER);
		verify(defender).setLeaderPriority(0);
		verify(defender).setRangedBand(4.0, 7.0);
		verify(marksman).setFanPlacement(NpcFanPlacement.ANY);
		verify(marksman).setRangedBand(8.0, 10.0);
	}

	@Test
	@DisplayName("applyRole leaves the settings.yml band alone for a role with none, and does nothing with no role")
	void applyRole_noBand_noRole() {
		CopNpc commander = mock(CopNpc.class);
		CopNpc plain     = mock(CopNpc.class);

		CopNpcFactory.applyRole(commander, COMMANDER, 10.0);
		CopNpcFactory.applyRole(plain, null, 10.0);

		verify(commander).setLeaderPriority(2);
		verify(commander, never()).setRangedBand(anyDouble(), anyDouble());
		verifyNoInteractions(plain);
	}

	private static CopTierConfig tier() {
		return new CopTierConfig(3, "&1Lieutenant", 30.0, 4.0, 1.2, 4.0, true, false, List.of(), List.of(), null,
		                         null, null, null, NpcDifficulty.NORMAL, TacticsConfig.DEFAULT, 0.1);
	}
}
