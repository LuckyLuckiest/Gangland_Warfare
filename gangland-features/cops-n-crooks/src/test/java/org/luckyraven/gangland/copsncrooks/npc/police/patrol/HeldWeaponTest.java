package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.bartizan.api.weapon.WeaponCatalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link HeldWeapon}: a fresh ServicesManager lookup every call; Bartizan absent means "not holding". */
@DisplayName("HeldWeapon - Bartizan weapon in the main hand")
class HeldWeaponTest {

	private ServicesManager                       services;
	private RegisteredServiceProvider<BartizanApi> registration;
	private WeaponCatalog                         weapons;
	private Player                                player;
	private ItemStack                             held;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		services     = mock(ServicesManager.class);
		registration = mock(RegisteredServiceProvider.class);
		BartizanApi api = mock(BartizanApi.class);
		weapons = mock(WeaponCatalog.class);
		when(api.weapons()).thenReturn(weapons);
		when(registration.getProvider()).thenReturn(api);

		player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		held = mock(ItemStack.class);
		when(inventory.getItemInMainHand()).thenReturn(held);
		when(player.getInventory()).thenReturn(inventory);
	}

	@Test
	@DisplayName("a Bartizan weapon in the main hand is held")
	void weaponInHand_isHeld() {
		when(services.getRegistration(BartizanApi.class)).thenReturn(registration);
		when(weapons.isWeapon(held)).thenReturn(true);

		assertTrue(HeldWeapon.holding(services, player));
	}

	@Test
	@DisplayName("any other item is not a weapon")
	void otherItem_notHeld() {
		when(services.getRegistration(BartizanApi.class)).thenReturn(registration);
		when(weapons.isWeapon(held)).thenReturn(false);

		assertFalse(HeldWeapon.holding(services, player));
	}

	@Test
	@DisplayName("Bartizan not registered: not holding, no error")
	void bartizanAbsent_notHeld() {
		when(services.getRegistration(BartizanApi.class)).thenReturn(null);

		assertFalse(HeldWeapon.holding(services, player));
	}

	@Test
	@DisplayName("a registration without a provider: not holding")
	void providerNull_notHeld() {
		when(services.getRegistration(BartizanApi.class)).thenReturn(registration);
		when(registration.getProvider()).thenReturn(null);

		assertFalse(HeldWeapon.holding(services, player));
	}

	@Test
	@DisplayName("resolved fresh on every call: Bartizan enabling later is seen")
	void resolvedOnEveryCall() {
		when(services.getRegistration(BartizanApi.class)).thenReturn(null, registration);
		when(weapons.isWeapon(held)).thenReturn(true);

		assertFalse(HeldWeapon.holding(services, player));
		assertTrue(HeldWeapon.holding(services, player));
	}
}
