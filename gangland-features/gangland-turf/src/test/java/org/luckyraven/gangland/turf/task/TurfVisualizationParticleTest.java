package org.luckyraven.gangland.turf.task;

import com.cryptomorin.xseries.particles.XParticle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T-942: the configured {@code Visualization_Particle} name is upper-cased with {@link Locale#ROOT}, so a Turkish
 * default locale does not turn the {@code i} of {@code crit} into a dotted {@code İ}.
 */
@DisplayName("TurfVisualization.resolveParticle - locale independent (T-942)")
class TurfVisualizationParticleTest {

	private final Locale defaultLocale = Locale.getDefault();

	@AfterEach
	void tearDown() {
		Locale.setDefault(defaultLocale);
	}

	@Test
	@DisplayName("a lower-case name containing an i still resolves under a Turkish default locale")
	void lowerCaseNameWithI_turkishLocale_resolves() throws Exception {
		Method resolve = TurfVisualization.class.getDeclaredMethod("resolveParticle", String.class);
		resolve.setAccessible(true);
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));

		Object resolved = resolve.invoke(null, "crit");

		assertEquals(XParticle.CRIT.get(), resolved, "crit must resolve to CRIT, not fall back to FLAME");
	}
}
