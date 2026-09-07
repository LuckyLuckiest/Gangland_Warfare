package org.luckyraven.gangland.weapon.projectile.recoil;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.compatibility.recoil.RecoilCompatibility;
import org.luckyraven.gangland.weapon.dto.RecoilData;
import org.luckyraven.gangland.weapon.dto.ScopeData;
import org.luckyraven.gangland.weapon.support.WeaponFixtures;
import org.luckyraven.gangland.weapon.types.gun.GunWeapon;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link RecoilManager}'s pattern-index advance, sneak/scope dampening and malformed-pattern fallback
 * (weapons.md W20 — Recoil and spread).
 *
 * <p>{@link RecoilCompatibility} is a concrete class, not an interface, but it is not {@code final} so Mockito can
 * mock it directly — the assertion is "did {@code RecoilManager} compute and forward the right yaw/pitch",
 * independent of what the compatibility layer then does with them (that is {@code RecoilCompatibility}'s own
 * concern, covered separately by W22 — NMS recoil adapter selection, which is integration-only).
 */
@DisplayName("RecoilManager — pattern index advance and default-recoil fallback")
class RecoilManagerTest {

	@Test
	@DisplayName("empty pattern falls back to default recoil (Recoil.Amount applied to both yaw and pitch)")
	void applyRecoil_emptyPattern_usesDefaultRecoil() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		weapon.setRecoilData(recoilData(1.5, List.of()));
		RecoilManager manager = new RecoilManager(weapon);

		RecoilCompatibility compat = mock(RecoilCompatibility.class);
		Player player = mock(Player.class); // isSneaking() defaults to false

		manager.applyRecoil(compat, player);

		verify(compat).modifyCameraRotation(player, 1.5f, 1.5f, true);
	}

	@Test
	@DisplayName("a configured pattern is applied entry-by-entry and the index advances, looping back to 0")
	void applyRecoil_pattern_advancesAndLoops() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		List<String[]> pattern = new ArrayList<>();
		pattern.add(new String[]{"1.0", "2.0"});
		pattern.add(new String[]{"3.0", "4.0"});
		weapon.setRecoilData(recoilData(9.0, pattern));
		RecoilManager manager = new RecoilManager(weapon);

		RecoilCompatibility compat = mock(RecoilCompatibility.class);
		Player player = mock(Player.class);

		manager.applyRecoil(compat, player);
		verify(compat).modifyCameraRotation(player, 1.0f, 2.0f, true);

		manager.applyRecoil(compat, player);
		verify(compat).modifyCameraRotation(player, 3.0f, 4.0f, true);

		manager.applyRecoil(compat, player); // index loops back to entry 0
		verify(compat, times(2)).modifyCameraRotation(player, 1.0f, 2.0f, true);
	}

	@Test
	@DisplayName("a malformed pattern entry (non-numeric) falls back to default recoil instead of throwing")
	void applyRecoil_malformedPatternEntry_fallsBackToDefault() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		List<String[]> pattern = List.<String[]>of(new String[]{"not-a-number", "2.0"});
		weapon.setRecoilData(recoilData(2.0, pattern));
		RecoilManager manager = new RecoilManager(weapon);

		RecoilCompatibility compat = mock(RecoilCompatibility.class);
		Player player = mock(Player.class);

		manager.applyRecoil(compat, player);

		verify(compat).modifyCameraRotation(player, 2.0f, 2.0f, true);
	}

	@Test
	@DisplayName("sneaking quarters the default recoil; sneaking while scoped only halves it")
	void applyRecoil_sneakingAndScoped_dampens() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		weapon.setRecoilData(recoilData(8.0, List.of()));
		RecoilManager manager = new RecoilManager(weapon);

		RecoilCompatibility compat = mock(RecoilCompatibility.class);
		Player sneaking = mock(Player.class);
		when(sneaking.isSneaking()).thenReturn(true);

		manager.applyRecoil(compat, sneaking);
		verify(compat).modifyCameraRotation(sneaking, 2.0f, 2.0f, true); // 8.0/2=4.0, sneaking-not-scoped halves again -> 2.0

		ScopeData scope = new ScopeData();
		scope.setScoped(true);
		weapon.setScopeData(scope);

		manager.applyRecoil(compat, sneaking);
		verify(compat).modifyCameraRotation(sneaking, 4.0f, 4.0f, true); // sneaking+scoped only halves once -> 4.0
	}

	@Test
	@DisplayName("resetRecoilPattern restarts the index at 0")
	void resetRecoilPattern_restartsIndex() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		List<String[]> pattern = new ArrayList<>();
		pattern.add(new String[]{"1.0", "1.0"});
		pattern.add(new String[]{"5.0", "5.0"});
		weapon.setRecoilData(recoilData(9.0, pattern));
		RecoilManager manager = new RecoilManager(weapon);

		RecoilCompatibility compat = mock(RecoilCompatibility.class);
		Player player = mock(Player.class);

		manager.applyRecoil(compat, player); // consumes entry 0
		manager.resetRecoilPattern();
		manager.applyRecoil(compat, player); // back to entry 0, not entry 1

		verify(compat, times(2)).modifyCameraRotation(player, 1.0f, 1.0f, true);
	}

	private static RecoilData recoilData(double amount, List<String[]> pattern) {
		RecoilData data = new RecoilData();
		data.setAmount(amount);
		data.setPushVelocity(0.1);
		data.setPushPowerUp(0.1);
		data.setPattern(pattern);
		return data;
	}

}
