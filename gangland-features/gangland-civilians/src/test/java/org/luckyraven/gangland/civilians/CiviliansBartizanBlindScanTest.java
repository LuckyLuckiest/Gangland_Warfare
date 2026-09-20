package org.luckyraven.gangland.civilians;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.listener.gang.GangAllyWeaponImpactListener;
import org.luckyraven.gangland.core.testsupport.BartizanBlindScan;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WS7 G5b (civilians' sibling to gadget's G5): {@code gangland-civilians}' {@code module.yml} drops the hard,
 * fail-fast {@code Plugins: [Bartizan]} dependency, so no {@code @Configuration}/{@code @ListenerHandler}/
 * {@code @CommandHandler}/{@code @Repository} class (nor any {@code @Bean} return type) in this module may carry a
 * Bartizan type in its own declared method/constructor signature <strong>outside of a deliberately isolated
 * class</strong> — see {@link BartizanBlindScan}'s javadoc for the exact reflection sites this reproduces.
 *
 * <p>Before the fix, {@code CiviliansModuleConfig} carried a {@code combatEligibility()} {@code @Bean} returning
 * Bartizan's {@code CombatEligibility} directly on the always-scanned config class holding all 9 civilians beans —
 * unsafe was {@code [CiviliansModuleConfig]}, meaning ALL 9 beans were lost together on a Bartizan-less server.
 * After the fix the bean lives alone in {@link CombatEligibilityConfig}, so unsafe is exactly
 * {@code [CombatEligibilityConfig]} — that one class is still reported unsafe by a <em>raw</em> reflection scan
 * (its one bean must return Bartizan's own interface type to publish under it — the wave's one direction reversal,
 * so unlike every other Bartizan-typed signature in this module it cannot itself be made blind-reflection-safe),
 * but that is the accepted, isolated casualty this gate exists to achieve: Keystone's {@code ReflectionGuard.orSkip}
 * skips just this one small class in production (fault {@code reflection.type.missing}) and nothing else is lost.
 * {@code assertTrue(unsafe.isEmpty())} (gadget's {@code GadgetBartizanBlindScanTest} shape) can therefore never go
 * green for this module; this test instead pins that {@code CombatEligibilityConfig} is the ONLY casualty.
 */
@DisplayName("gangland-civilians — Bartizan-blind reflection scan (WS7 G5b)")
class CiviliansBartizanBlindScanTest {

	@Test
	@DisplayName("CombatEligibilityConfig is the only scanned civilians class unsafe without Bartizan")
	void onlyTheIsolatedCombatEligibilityBeanIsUnsafeWithoutBartizan() {
		List<String> unsafe = BartizanBlindScan.findUnsafeClasses("org.luckyraven.gangland.civilians");
		assertEquals(Set.of("org.luckyraven.gangland.civilians.CombatEligibilityConfig"), Set.copyOf(unsafe),
		             "Only CombatEligibilityConfig's own combatEligibility() bean should be unsafe - every other "
		             + "scanned civilians class (including CiviliansModuleConfig's other 8 beans) must stay "
		             + "reflection-safe: " + unsafe);
	}

	/**
	 * {@link BartizanBlindScan} treats ANY non-empty {@code @ListenerHandler} condition as "safe by construction"
	 * without checking which condition — so the scan above cannot tell {@code condition = "isGangEnabled"}
	 * (semantically wrong: gates on the gang feature, not Bartizan availability) apart from
	 * {@code condition = "isBartizanAvailable"} (correct). This test pins the semantic fix directly.
	 */
	@Test
	@DisplayName("GangAllyWeaponImpactListener gates its own registration on Bartizan availability, not gang-enabled")
	void gangAllyWeaponImpactListenerGatesOnBartizanAvailability() {
		ListenerHandler annotation = GangAllyWeaponImpactListener.class.getAnnotation(ListenerHandler.class);
		assertEquals("isBartizanAvailable", annotation.condition());
	}
}
