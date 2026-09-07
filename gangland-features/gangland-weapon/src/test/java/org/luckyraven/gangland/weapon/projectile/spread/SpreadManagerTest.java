package org.luckyraven.gangland.weapon.projectile.spread;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.weapon.dto.SpreadData;
import org.luckyraven.gangland.weapon.support.WeaponFixtures;
import org.luckyraven.gangland.weapon.types.gun.GunWeapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link SpreadManager}'s spread accumulation, bounds clamping and reset behaviour (weapons.md W20 — Recoil
 * and spread).
 *
 * <p>Pins Observation #8 (weapons.md): {@code SpreadData.resetTime} is authored in YAML as {@code Time:} ticks
 * (e.g. {@code Time: 5}), but {@link SpreadManager#applySpread} compares it against a millisecond delta
 * ({@code System.currentTimeMillis()}). A shot fired even a few milliseconds after the previous one — which is
 * every real shot, since 5 ticks = 250 ms is far larger than the sub-millisecond gap between two calls in a test
 * (and larger than any human's real trigger cadence being mistaken for "no reset") — resets to
 * {@code Starting_Spread} instead of accumulating. This test proves the reset fires on back-to-back calls even
 * though 5 <i>ticks</i> have obviously not elapsed.
 */
@DisplayName("SpreadManager — spread accumulation, bounds, and the ticks-vs-milliseconds reset bug")
class SpreadManagerTest {

	private static SpreadData spreadData(double start, int resetTimeTicks, double changeBase, boolean resetOnBound,
	                                     double min, double max) {
		SpreadData data = new SpreadData();
		data.setStart(start);
		data.setResetTime(resetTimeTicks);
		data.setChangeBase(changeBase);
		data.setResetOnBound(resetOnBound);
		data.setBoundMinimum(min);
		data.setBoundMaximum(max);
		return data;
	}

	@Test
	@DisplayName("applySpread with no SpreadData configured returns the original vector unchanged")
	void applySpread_noSpreadData_returnsOriginal() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		SpreadManager manager = new SpreadManager(weapon);
		Vector original = new Vector(1, 0, 0);

		assertSame(original, manager.applySpread(original));
	}

	@Test
	@DisplayName("currentSpread starts at SpreadData.start")
	void currentSpread_startsAtConfiguredStart() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		weapon.setSpreadData(spreadData(0.05, 5, 0.02, false, 0.0, 0.5));
		SpreadManager manager = new SpreadManager(weapon);

		assertEquals(0.05, manager.getCurrentSpread());
	}

	@Test
	@DisplayName("Observation #8: resetTime authored as 'Time: 5' ticks (250ms) is compared as 5 milliseconds instead")
	void applySpread_resetTimeInTicks_resetsFarEarlierThanIntended() throws InterruptedException {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		// Time: 5 in YAML is meant as "5 ticks" (250ms at 20 TPS) of no-fire before resetting — but the field is
		// compared against System.currentTimeMillis() deltas, i.e. as 5 *milliseconds*.
		weapon.setSpreadData(spreadData(0.0, 5, 0.10, false, 0.0, 1.0));
		SpreadManager manager = new SpreadManager(weapon);

		manager.applySpread(new Vector(0, 0, 1)); // first shot: 0.0 -> updateSpread -> 0.10
		assertEquals(0.10, manager.getCurrentSpread(), 0.0001);

		// Sleep 50ms: far short of the *intended* 250ms (5-tick) reset window, so correct tick semantics would
		// still be accumulating — but comfortably past the 5-millisecond threshold the code actually compares
		// against (and past Windows' coarse System.currentTimeMillis() tick granularity), so the reset fires
		// anyway.
		Thread.sleep(50);

		manager.applySpread(new Vector(0, 0, 1));
		assertEquals(0.10, manager.getCurrentSpread(), 0.0001,
		             "spread reset to Starting_Spread + one Change.Base after only ~50ms — nowhere near the "
		             + "5-tick (250ms) window the YAML author configured — instead of accumulating to 0.20");
	}

	@Test
	@DisplayName("updateSpread clamps at Bounds.Max without resetting when Reset_On_Bound is false")
	void applySpread_clampsAtMaxWithoutReset() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		// A very large resetTime keeps the millisecond-comparison bug (Observation #8) from firing within this
		// fast-running test, so the bounds-clamping logic can be exercised in isolation.
		weapon.setSpreadData(spreadData(0.0, Integer.MAX_VALUE, 1.0, false, 0.0, 0.5));
		SpreadManager manager = new SpreadManager(weapon);

		manager.applySpread(new Vector(0, 0, 1)); // 0.0 + 1.0 >= 0.5 -> clamp to boundMaximum
		assertEquals(0.5, manager.getCurrentSpread());

		manager.applySpread(new Vector(0, 0, 1)); // stays clamped
		assertEquals(0.5, manager.getCurrentSpread());
	}

	@Test
	@DisplayName("updateSpread resets to Starting_Spread at the bound when Reset_On_Bound is true")
	void applySpread_resetOnBound_resetsToStart() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		weapon.setSpreadData(spreadData(0.05, Integer.MAX_VALUE, 1.0, true, 0.0, 0.5));
		SpreadManager manager = new SpreadManager(weapon);

		manager.applySpread(new Vector(0, 0, 1)); // 0.05 + 1.0 >= 0.5 -> Reset_On_Bound -> back to Starting_Spread

		assertEquals(0.05, manager.getCurrentSpread());
	}

	@Test
	@DisplayName("resetSpread manually restores Starting_Spread")
	void resetSpread_restoresStart() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		weapon.setSpreadData(spreadData(0.02, Integer.MAX_VALUE, 1.0, false, 0.0, 5.0));
		SpreadManager manager = new SpreadManager(weapon);

		manager.applySpread(new Vector(0, 0, 1));
		assertTrue(manager.getCurrentSpread() > 0.02);

		manager.resetSpread();

		assertEquals(0.02, manager.getCurrentSpread());
	}

}
