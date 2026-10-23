package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.config.SightSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.SightModel;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SightModuleConfig - the SightModel bean is registered on CopPerception")
class SightModuleConfigTest {

	private static final double EPS = 1e-9;

	private final CopPerception       perception = new CopPerception();
	private final CopLoader           loader     = mock(CopLoader.class);
	private final CopConfigProvider   provider   = mock(CopConfigProvider.class);
	private final CivilianNpcRegistry civilians  = mock(CivilianNpcRegistry.class);
	private final CopManager          manager    = mock(CopManager.class);
	private final Player              player     = mock(Player.class);
	private final Block               block      = mock(Block.class);
	private final CopNpc              cop        = mock(CopNpc.class);
	private final CopTierConfig       tier       = mock(CopTierConfig.class);

	SightModuleConfigTest() {
		World           world     = mock(World.class);
		Location        eyes      = mock(Location.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getName()).thenReturn("Bob");
		when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		when(player.getEyeLocation()).thenReturn(eyes);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
		when(eyes.getWorld()).thenReturn(world);
		when(eyes.getBlock()).thenReturn(block);
		when(block.getLightLevel()).thenReturn((byte) 2);
		when(civilians.getActiveNpcs()).thenReturn(List.of());
		when(cop.getTierConfig()).thenReturn(tier);
		when(loader.getLoadedProvider()).thenReturn(provider);
		when(provider.getSightSettings()).thenReturn(SightSettings.DEFAULT);
	}

	private SightModel bean() {
		return new SightModuleConfig().sightModel(perception, loader, civilians, manager);
	}

	@Test
	@DisplayName("the bean exists and registers itself: perception.rangeFor in a dark alley is 0.4 x base")
	void registeredOnPerception() {
		assertNotNull(bean());

		assertEquals(16.0, perception.rangeFor(cop, player, 40.0), EPS);
	}

	@Test
	@DisplayName("squad pursuit and the no-squad branch both use the factor: pursuitRange 16 with a null or stale squad")
	void pursuitRangeReachesFactor() {
		bean();
		NpcSquad stale = mock(NpcSquad.class);

		assertEquals(16.0, perception.pursuitRange(cop, player, null, 40.0), EPS);
		assertEquals(16.0, perception.pursuitRange(cop, player, stale, 40.0), EPS);
	}

	@Test
	@DisplayName("an Ignore_Darkness tier keeps its full range in the dark")
	void ignoreDarknessTierThroughPerception() {
		bean();
		when(tier.ignoreDarkness()).thenReturn(true);

		assertEquals(40.0, perception.rangeFor(cop, player, 40.0), EPS);
	}

	@Test
	@DisplayName("settings follow the loaded provider live (reload): a new Dark_Factor applies without rebuilding the bean")
	void settingsFollowProvider() {
		bean();
		when(provider.getSightSettings()).thenReturn(new SightSettings(true, 4, 0.5, 8, 0.7, 0.6, 3, 4.0, 2, 0.7, 0.25, 5));

		assertEquals(20.0, perception.rangeFor(cop, player, 40.0), EPS);
	}

	@Test
	@DisplayName("no loaded provider -> SightSettings.DEFAULT")
	void noProviderUsesDefaults() {
		when(loader.getLoadedProvider()).thenReturn(null);
		bean();

		assertEquals(16.0, perception.rangeFor(cop, player, 40.0), EPS);
	}

	@Test
	@DisplayName("Sight.Enable false leaves every range untouched (0.19 behaviour)")
	void disabledLeavesRangeAlone() {
		when(provider.getSightSettings()).thenReturn(new SightSettings(false, 4, 0.4, 8, 0.7, 0.6, 3, 4.0, 2, 0.7, 0.25, 5));
		bean();

		assertEquals(40.0, perception.rangeFor(cop, player, 40.0), EPS);
	}
}
