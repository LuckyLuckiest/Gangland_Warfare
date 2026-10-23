package org.luckyraven.gangland.npcshops.holdup;

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

@DisplayName("BartizanAim - fresh ServicesManager lookup, never cached")
class BartizanAimTest {

	private ServicesManager services;
	private Player          player;
	private ItemStack       held;
	private WeaponCatalog   weapons;

	@SuppressWarnings("unchecked")
	private RegisteredServiceProvider<BartizanApi> registration() {
		BartizanApi api = mock(BartizanApi.class);
		when(api.weapons()).thenReturn(weapons);
		RegisteredServiceProvider<BartizanApi> rsp = mock(RegisteredServiceProvider.class);
		when(rsp.getProvider()).thenReturn(api);
		return rsp;
	}

	@BeforeEach
	void setUp() {
		services = mock(ServicesManager.class);
		player   = mock(Player.class);
		held     = mock(ItemStack.class);
		weapons  = mock(WeaponCatalog.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getItemInMainHand()).thenReturn(held);
	}

	@Test
	@DisplayName("no BartizanApi registration means no weapon")
	void noRegistration() {
		when(services.getRegistration(BartizanApi.class)).thenReturn(null);
		assertFalse(BartizanAim.weaponHeld(services, player));
	}

	@Test
	@DisplayName("a Bartizan weapon in the main hand is a weapon")
	void weaponInMainHand() {
		RegisteredServiceProvider<BartizanApi> rsp = registration();
		when(services.getRegistration(BartizanApi.class)).thenReturn(rsp);
		when(weapons.isWeapon(held)).thenReturn(true);
		assertTrue(BartizanAim.weaponHeld(services, player));
	}

	@Test
	@DisplayName("a non-weapon item in hand is not a weapon")
	void plainItem() {
		RegisteredServiceProvider<BartizanApi> rsp = registration();
		when(services.getRegistration(BartizanApi.class)).thenReturn(rsp);
		when(weapons.isWeapon(held)).thenReturn(false);
		assertFalse(BartizanAim.weaponHeld(services, player));
	}

	@Test
	@DisplayName("the registration is looked up on every call (Bartizan may enable or vanish later)")
	void resolvedFreshEveryCall() {
		when(weapons.isWeapon(held)).thenReturn(true);
		RegisteredServiceProvider<BartizanApi> rsp = registration();
		when(services.getRegistration(BartizanApi.class)).thenReturn(null, rsp, null);
		assertFalse(BartizanAim.weaponHeld(services, player));
		assertTrue(BartizanAim.weaponHeld(services, player));
		assertFalse(BartizanAim.weaponHeld(services, player));
	}

}
