package org.luckyraven.gangland.gadget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BartizanBlindScan;

import java.net.URLClassLoader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WS7 G5: {@code gangland-gadget}'s {@code module.yml} drops the hard, fail-fast {@code Plugins: [Bartizan]}
 * dependency, so no {@code @Configuration}/{@code @ListenerHandler}/{@code @CommandHandler}/{@code @Repository}
 * class (nor any {@code @Bean} return type) in this module may carry a Bartizan type in its own declared
 * method/constructor signature — see {@link BartizanBlindScan}'s javadoc for the exact reflection sites this
 * reproduces.
 *
 * <p>Before the split (B5/B6), {@code CarDamageListener} carried {@code onWeaponEntityDamage}/
 * {@code onWeaponRaytraceImpact} directly in an always-scanned {@code @ListenerHandler} class, naming
 * {@code WeaponEntityDamageEvent}/{@code WeaponRaytraceImpactEvent} in their own signatures — this test's standard
 * scan is red against that shape (recorded in the gate report) and green now that those two handlers live in the
 * condition-gated {@code CarWeaponDamageListener} instead.
 */
@DisplayName("gangland-gadget — Bartizan-blind reflection scan (WS7 G5)")
class GadgetBartizanBlindScanTest {

	@Test
	@DisplayName("no scanned gadget class exposes a Bartizan type in its own signature")
	void noScannedClassCrashesWithoutBartizan() {
		List<String> unsafe = BartizanBlindScan.findUnsafeClasses("org.luckyraven.gangland.gadget");
		assertTrue(unsafe.isEmpty(), "Classes unsafe without Bartizan: " + unsafe);
	}

	@Test
	@DisplayName("JetpackBartizanTraitBridge and CarMeleeWeaponLookup carry no scan annotation, so the standard "
	             + "scan never looks at them — force-load each explicitly to prove they are safe too")
	void staticHelpersLoadCleanUnderBlindLoader() {
		URLClassLoader blind = BartizanBlindScan.bartizanBlindClassLoader();

		assertThrows(ClassNotFoundException.class,
		             () -> Class.forName("org.luckyraven.bartizan.api.BartizanApi", false, blind),
		             "the blind loader must genuinely be unable to resolve Bartizan types");

		Class<?> jetpackBridge = assertDoesNotThrow(() -> Class.forName(
				"org.luckyraven.gangland.gadget.jetpack.config.JetpackBartizanTraitBridge", false, blind),
				"JetpackBartizanTraitBridge itself must be loadable even when Bartizan is absent - proves it is "
				+ "never linked unless its one guarded call site actually executes");
		assertDoesNotThrow(jetpackBridge::getDeclaredMethods,
		                   "the bridge's own method signatures must not name a Bartizan type either");

		Class<?> carMeleeLookup = assertDoesNotThrow(() -> Class.forName(
				"org.luckyraven.gangland.gadget.listener.car.CarMeleeWeaponLookup", false, blind),
				"CarMeleeWeaponLookup itself must be loadable even when Bartizan is absent - proves it is never "
				+ "linked unless its guarded call sites in CarDamageListener actually execute");
		// getMethods() (public surface only), not getDeclaredMethods(): unlike JetpackBartizanTraitBridge, this
		// class has a deliberately Bartizan-typed PRIVATE weapons() helper (B5's design) that nothing in Keystone's
		// reflection scan ever touches (the class carries no scan annotation at all) - getDeclaredMethods() would
		// eagerly resolve that private method's WeaponCatalog return type too and false-positive here.
		assertDoesNotThrow(carMeleeLookup::getMethods,
		                   "the lookup's public method signatures must not name a Bartizan type either");

		assertDoesNotThrow(blind::close);
	}
}
