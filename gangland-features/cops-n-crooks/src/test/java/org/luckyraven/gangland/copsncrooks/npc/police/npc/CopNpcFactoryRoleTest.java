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
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.config.YamlCopConfigProvider;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehaviorFactory;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.diagnostics.Diagnostics;
import org.luckyraven.keystone.diagnostics.Fault;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.npc.spi.NpcRangedAttack;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
	/** A gun Bartizan resolved: the role's band applies. */
	private static final NpcRangedAttack GUN = mock(NpcRangedAttack.class);

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

		CopNpcFactory.applyRole(defender, DEFENDER, GUN, 30.0);
		CopNpcFactory.applyRole(marksman, MARKSMAN, GUN, 10.0); // the live rifle: Distance 10

		verify(defender).setFanPlacement(NpcFanPlacement.CENTER);
		verify(defender).setLeaderPriority(0);
		verify(defender).setRangedBand(4.0, 7.0);
		verify(marksman).setFanPlacement(NpcFanPlacement.ANY);
		verify(marksman).setRangedBand(8.0, 10.0);
	}

	@Test
	@DisplayName("the built-in Marksman holds 22-32 blocks out with a scout (Distance 100) or an awp (120): far behind every other role")
	void applyRole_builtInMarksman_holdsFar() {
		CopRole marksman = YamlCopConfigProvider.builtInRoles(RetreatSettings.DEFAULT).get("Marksman");
		CopNpc  scout    = mock(CopNpc.class);
		CopNpc  awp      = mock(CopNpc.class);

		CopNpcFactory.applyRole(scout, marksman, GUN, 100.0);
		CopNpcFactory.applyRole(awp, marksman, GUN, 120.0);

		verify(scout).setRangedBand(22.0, 32.0);
		verify(awp).setRangedBand(22.0, 32.0);
	}

	@Test
	@DisplayName("the loadout puts the off hand of the role's kit for the cop's own tier (the Medic's golden apple at Military)")
	void loadout_offHandFromTierKit() {
		NPC             npc       = mock(NPC.class, RETURNS_DEEP_STUBS);
		Player          entity    = mock(Player.class);
		EntityEquipment equipment = mock(EntityEquipment.class);
		when(entity.getEquipment()).thenReturn(equipment);
		when(entity.getAttribute(XAttribute.MAX_HEALTH.get())).thenReturn(mock(AttributeInstance.class));
		when(npc.isSpawned()).thenReturn(true);
		when(npc.getEntity()).thenReturn(entity);
		CopTierConfig military = new CopTierConfig(5, "&4Military", 60.0, 7.0, 1.4, 5.0, true, true, List.of(),
		                                           List.of(), null, null, null, null, NpcDifficulty.DEADLY,
		                                           TacticsConfig.DEFAULT, 0.1);
		CopNpc cop = new CopNpc(mock(JavaPlugin.class), npc, military, Map.of(), new Location(null, 0, 0, 0),
		                        mock(CopConfigProvider.class));
		cop.setRole(new CopRole("Medic", "Medic", NpcFanPlacement.CENTER, null, null, 1.0, 0, null, 1.0, 0, null, 0,
		                        60, true, false, "&c", "", CopRole.Kit.EMPTY,
		                        Map.of(5, new CopRole.Kit(null, List.of(), null, null, null, null,
		                                                  new CopRole.Gear(Material.GOLDEN_APPLE, null, false)))));

		cop.setLoadout(CopNpcFactory.loadout(cop, military, mock(NpcMarkManager.class), null));

		ArgumentCaptor<ItemStack> offHand = ArgumentCaptor.forClass(ItemStack.class);
		verify(equipment).setItemInOffHand(offHand.capture());
		assertEquals(Material.GOLDEN_APPLE, offHand.getValue().getType());
	}

	@Test
	@DisplayName("applyRole leaves the settings.yml band alone for a role with none, and does nothing with no role")
	void applyRole_noBand_noRole() {
		CopNpc commander = mock(CopNpc.class);
		CopNpc plain     = mock(CopNpc.class);

		CopNpcFactory.applyRole(commander, COMMANDER, GUN, 10.0);
		CopNpcFactory.applyRole(plain, null, GUN, 10.0);

		verify(commander).setLeaderPriority(2);
		verify(commander, never()).setRangedBand(anyDouble(), anyDouble());
		verifyNoInteractions(plain);
	}

	@Test
	@DisplayName("no gun resolved (Bartizan missing, a Weapon_Pool typo): placement and priority set, the role band not - no 22-32 stand-off holding a fallback item that never fires")
	void applyRole_noGun_keepsSettingsBand() {
		CopNpc marksman = mock(CopNpc.class);

		CopNpcFactory.applyRole(marksman, YamlCopConfigProvider.builtInRoles(RetreatSettings.DEFAULT).get("Marksman"),
		                        NpcRangedAttack.NONE, null);

		verify(marksman).setFanPlacement(NpcFanPlacement.ANY);
		verify(marksman).setLeaderPriority(0);
		verify(marksman, never()).setRangedBand(anyDouble(), anyDouble());
	}

	@Test
	@DisplayName("a role gun Bartizan does not know is reported once per name, not on every spawn")
	void unresolvedRoleWeapon_reportedOncePerName() {
		List<Fault> faults = new ArrayList<>();
		Diagnostics hub    = new Diagnostics(null).addSink(faults::add);
		Diagnostics.install(hub);
		try {
			CopNpcFactory factory = new CopNpcFactory(mock(JavaPlugin.class), mock(CopConfigProvider.class),
			                                          mock(CopBehaviorFactory.class), mock(NpcMarkManager.class),
			                                          mock(BartizanNpcWeapons.class), mock(DownedTargetFilter.class));

			factory.reportUnresolvedWeapon(MARKSMAN, "scuot");
			factory.reportUnresolvedWeapon(MARKSMAN, "scuot");
			factory.reportUnresolvedWeapon(DEFENDER, "shotgnu");

			assertEquals(2, faults.size(), faults::toString);
			assertTrue(faults.get(0).message().contains("scuot") && faults.get(0).message().contains("Marksman"),
			           faults.get(0)::message);
		} finally {
			Diagnostics.uninstall(hub);
		}
	}

	private static CopTierConfig tier() {
		return new CopTierConfig(3, "&1Lieutenant", 30.0, 4.0, 1.2, 4.0, true, false, List.of(), List.of(), null,
		                         null, null, null, NpcDifficulty.NORMAL, TacticsConfig.DEFAULT, 0.1);
	}
}
