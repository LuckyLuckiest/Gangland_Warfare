package org.luckyraven.gangland.weapon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.weapon.configuration.WeaponAddon;
import org.luckyraven.gangland.weapon.support.WeaponFixtures;
import org.luckyraven.gangland.weapon.types.gun.GunWeapon;
import org.luckyraven.gangland.weapon.types.throwable.ThrowableWeapon;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the read-only lookup seams added for WP-06 (P0, weapons.md observation #6).
 *
 * <p>Every {@code getWeapon(type)} call with a null uuid minted a fresh {@link Weapon} with a random uuid and put it
 * into {@code WeaponService.weapons}. That map is the autosave data supplier ({@code WeaponManager.initialize}), so
 * item converters, item refreshers, shop display names and throwable death messages each grew the registry — and the
 * {@code weapon} table — by one row per call, without bound.
 *
 * <p>{@code getWeaponTemplate} / {@code getWeaponTemplates} / {@code createTransientWeapon} give those callers a
 * lookup that never registers anything; only a real give ({@code getWeapon(...)}) still mints.
 */
@DisplayName("WeaponService — template lookups never grow the registry (WP-06)")
class WeaponServiceTest {

	private WeaponAddon     addon;
	private WeaponService   service;
	private GunWeapon       gunTemplate;
	private ThrowableWeapon throwableTemplate;

	@BeforeEach
	void setUp() {
		addon             = mock(WeaponAddon.class);
		gunTemplate       = WeaponFixtures.gunWeapon(30, 1);
		throwableTemplate = WeaponFixtures.throwableWeapon(1);

		when(addon.getWeapon("test_gun")).thenReturn(gunTemplate);
		when(addon.getWeapon("test_grenade")).thenReturn(throwableTemplate);
		when(addon.getWeapons()).thenReturn(List.of(gunTemplate, throwableTemplate));

		service = new WeaponService(addon) {
		};
	}

	@Test
	@DisplayName("getWeaponTemplate hands back the shared catalogue entry and registers nothing")
	void getWeaponTemplate_registersNothing() {
		Weapon template = service.getWeaponTemplate("test_gun");

		assertSame(gunTemplate, template);
		assertTrue(service.getWeapons().isEmpty(),
		           "a read-only template lookup must not mint a registry entry (and a weapon table row)");
	}

	@Test
	@DisplayName("getWeaponTemplate returns null for a null or unknown type instead of minting")
	void getWeaponTemplate_unknownType_returnsNull() {
		assertNull(service.getWeaponTemplate(null));
		assertNull(service.getWeaponTemplate(""));
		assertNull(service.getWeaponTemplate("not_a_weapon"));
		assertTrue(service.getWeapons().isEmpty());
	}

	@Test
	@DisplayName("getWeaponTemplates exposes the whole configured catalogue, not just the instances minted so far")
	void getWeaponTemplates_exposesCatalogue() {
		assertEquals(2, service.getWeaponTemplates().size());
		assertTrue(service.getWeaponTemplates().contains(gunTemplate));
		assertTrue(service.getWeapons().isEmpty());
	}

	@Test
	@DisplayName("createTransientWeapon builds a usable copy without registering it")
	void createTransientWeapon_registersNothing() {
		Weapon first  = service.createTransientWeapon("test_gun");
		Weapon second = service.createTransientWeapon("test_gun");

		assertNotNull(first);
		assertNotNull(second);
		assertNotSame(gunTemplate, first);
		assertNotSame(first, second);
		assertNotNull(first.getUuid());
		assertTrue(service.getWeapons().isEmpty(),
		           "converters and refreshers must not add a row per item they build");
	}

	@Test
	@DisplayName("createTransientWeapon keeps the deterministic per-type uuid for throwables so items still stack")
	void createTransientWeapon_throwable_usesDeterministicUuid() {
		UUID expected = UUID.nameUUIDFromBytes(("throwable:test_grenade").getBytes(StandardCharsets.UTF_8));

		Weapon first  = service.createTransientWeapon("test_grenade");
		Weapon second = service.createTransientWeapon("test_grenade");

		assertNotNull(first);
		assertNotNull(second);
		assertEquals(expected, first.getUuid());
		assertEquals(expected, second.getUuid());
	}

	@Test
	@DisplayName("createTransientWeapon returns null for an unknown type")
	void createTransientWeapon_unknownType_returnsNull() {
		assertNull(service.createTransientWeapon("not_a_weapon"));
		assertNull(service.createTransientWeapon(null));
	}

	@Test
	@DisplayName("an actual give still mints and registers exactly one instance")
	void getWeapon_actualGive_stillRegisters() {
		Weapon given = service.getWeapon(null, null, "test_gun", true);

		assertNotNull(given);
		assertEquals(1, service.getWeapons().size());
		assertSame(given, service.getWeapons().get(given.getUuid()));
	}

}
