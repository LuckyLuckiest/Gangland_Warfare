package org.luckyraven.gangland.civilians.npc.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.bartizan.api.item.WeaponItemApi;
import org.luckyraven.bartizan.api.npc.NpcWeaponController;
import org.luckyraven.bartizan.api.npc.NpcWeaponFactory;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.spi.NpcRangedAttack;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link BartizanNpcWeapons}' weapon-name validation guard (T-HR1, gate-H review M1) and held-item builder
 * (T-HR2, gate-H review M2). Bartizan's {@code NpcWeaponFactoryImpl.create} throws {@code IllegalArgumentException}
 * for a name it doesn't recognise (Bartizan commit {@code 38913d5}), so {@link BartizanNpcWeapons#create} and
 * {@link BartizanNpcWeapons#buildItem} must both consult {@code WeaponItemApi#isValidWeaponName} before touching the
 * name — a bare-material {@code weaponNamePool()} entry must degrade to {@link NpcRangedAttack#NONE}/{@code null}
 * instead of aborting the civilian's spawn.
 */
@DisplayName("BartizanNpcWeapons — weapon-name validation and item build (T-HR1/T-HR2)")
class BartizanNpcWeaponsTest {

	private final BartizanNpcWeapons weapons = new BartizanNpcWeapons();

	@SuppressWarnings("unchecked")
	private MockedStatic<Bukkit> mockBukkit(BartizanApi api) {
		MockedStatic<Bukkit>                   bukkit          = mockStatic(Bukkit.class);
		ServicesManager                        servicesManager = mock(ServicesManager.class);
		RegisteredServiceProvider<BartizanApi> rsp             = mock(RegisteredServiceProvider.class);
		when(rsp.getProvider()).thenReturn(api);
		when(servicesManager.getRegistration(BartizanApi.class)).thenReturn(rsp);
		bukkit.when(Bukkit::getServicesManager).thenReturn(servicesManager);
		return bukkit;
	}

	// ── T-HR1: create() ──────────────────────────────────────────────────────

	@Test
	@DisplayName("create() returns NONE for a name Bartizan does not recognise, instead of letting the factory throw")
	void create_unresolvableWeaponName_returnsNone() {
		BartizanApi    api   = mock(BartizanApi.class);
		WeaponItemApi  items = mock(WeaponItemApi.class);
		when(api.items()).thenReturn(items);
		when(items.isValidWeaponName("bogus")).thenReturn(false);

		try (MockedStatic<Bukkit> ignored = mockBukkit(api)) {
			NpcRangedAttack result = weapons.create(mock(LivingEntity.class), "bogus", NpcDifficulty.NORMAL);

			assertSame(NpcRangedAttack.NONE, result);
			verify(api, never()).npcWeapons();
		}
	}

	@Test
	@DisplayName("create() resolves the factory's controller for a name Bartizan recognises")
	void create_validWeaponName_resolvesController() {
		BartizanApi         api        = mock(BartizanApi.class);
		WeaponItemApi       items      = mock(WeaponItemApi.class);
		NpcWeaponFactory    factory    = mock(NpcWeaponFactory.class);
		NpcWeaponController controller = mock(NpcWeaponController.class);
		LivingEntity         shooter    = mock(LivingEntity.class);
		when(api.items()).thenReturn(items);
		when(items.isValidWeaponName("rifle")).thenReturn(true);
		when(api.npcWeapons()).thenReturn(factory);
		when(factory.create(shooter, "rifle", NpcDifficulty.NORMAL.getFireRateMultiplier(),
		                    NpcDifficulty.NORMAL.getAimError())).thenReturn(controller);

		try (MockedStatic<Bukkit> ignored = mockBukkit(api)) {
			NpcRangedAttack result = weapons.create(shooter, "rifle", NpcDifficulty.NORMAL);

			assertSame(controller, result);
		}
	}

	// ── T-HR2: buildItem() ───────────────────────────────────────────────────

	@Test
	@DisplayName("buildItem() returns null for a name Bartizan does not recognise")
	void buildItem_unresolvableWeaponName_returnsNull() {
		BartizanApi   api   = mock(BartizanApi.class);
		WeaponItemApi items = mock(WeaponItemApi.class);
		when(api.items()).thenReturn(items);
		when(items.isValidWeaponName("bogus")).thenReturn(false);

		try (MockedStatic<Bukkit> ignored = mockBukkit(api)) {
			assertNull(weapons.buildItem("bogus"));
			verify(items, never()).buildItem(any());
		}
	}

	@Test
	@DisplayName("buildItem() builds the item for a name Bartizan recognises")
	void buildItem_validWeaponName_buildsItem() {
		BartizanApi   api   = mock(BartizanApi.class);
		WeaponItemApi items = mock(WeaponItemApi.class);
		ItemStack     stack = mock(ItemStack.class);
		when(api.items()).thenReturn(items);
		when(items.isValidWeaponName("rifle")).thenReturn(true);
		when(items.buildItem("rifle")).thenReturn(stack);

		try (MockedStatic<Bukkit> ignored = mockBukkit(api)) {
			assertSame(stack, weapons.buildItem("rifle"));
		}
	}

	@Test
	@DisplayName("buildItem() returns null when Bartizan is absent")
	void buildItem_bartizanAbsent_returnsNull() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			ServicesManager servicesManager = mock(ServicesManager.class);
			when(servicesManager.getRegistration(BartizanApi.class)).thenReturn(null);
			bukkit.when(Bukkit::getServicesManager).thenReturn(servicesManager);

			assertNull(weapons.buildItem("rifle"));
		}
	}

	@Test
	@DisplayName("buildItem() returns null for a null weapon name")
	void buildItem_nullWeaponName_returnsNull() {
		assertNull(weapons.buildItem(null));
	}

}
