package org.luckyraven.gangland.civilians.npc.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.spi.NpcRangedAttack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B7 guard (WS7 G5b fix round 1, review C2): {@code BartizanNpcWeapons.create}/{@code .buildItem} resolved
 * {@code BartizanApi.class} before any Bartizan-availability check, so on a real Bartizan-less server (the jar not
 * even on the classpath) the class literal itself would fail to resolve ({@code NoClassDefFoundError}) - the
 * existing {@code rsp == null} check never got a chance to run. With {@code Settings.isBartizanAvailable()} stubbed
 * {@code false} and {@link org.bukkit.plugin.ServicesManager} deliberately left unstubbed (a default Mockito mock
 * returns {@code null} from an unstubbed object-returning method - the unit-test stand-in for the jar being absent
 * entirely, same reasoning {@code CarMeleeWeaponLookupGuardTest} already established for the identical shape), both
 * methods must short-circuit before ever touching the ServicesManager or the {@code BartizanApi} type.
 */
@DisplayName("BartizanNpcWeapons — Bartizan-unavailable guard (WS7 G5b fix round 1, B7/C2)")
class BartizanNpcWeaponsGuardTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
	}

	@Test
	@DisplayName("create(): Bartizan unavailable returns NpcRangedAttack.NONE without touching the ServicesManager")
	void create_bartizanUnavailable_returnsNone() {
		stubBartizanUnavailable();

		BartizanNpcWeapons weapons = new BartizanNpcWeapons();
		LivingEntity        shooter = mock(LivingEntity.class);

		NpcRangedAttack result = weapons.create(shooter, "some_weapon", NpcDifficulty.NORMAL);

		assertEquals(NpcRangedAttack.NONE, result);
	}

	@Test
	@DisplayName("buildItem(): Bartizan unavailable returns null without touching the ServicesManager")
	void buildItem_bartizanUnavailable_returnsNull() {
		stubBartizanUnavailable();

		BartizanNpcWeapons weapons = new BartizanNpcWeapons();

		ItemStack result = weapons.buildItem("some_weapon");

		assertNull(result);
	}

	private static void stubBartizanUnavailable() {
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.isPluginEnabled("Bartizan")).thenReturn(false);
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);
	}
}
