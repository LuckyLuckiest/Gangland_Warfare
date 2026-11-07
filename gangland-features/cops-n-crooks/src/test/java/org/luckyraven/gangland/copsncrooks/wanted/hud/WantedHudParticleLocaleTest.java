package org.luckyraven.gangland.copsncrooks.wanted.hud;

import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.Particle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("WantedHud.resolveParticle - locale independence")
class WantedHudParticleLocaleTest {

	private Locale original;

	@BeforeEach
	void setTurkishLocale() {
		original = Locale.getDefault();
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));
	}

	@AfterEach
	void restoreLocale() {
		Locale.setDefault(original);
	}

	@Test
	@DisplayName("T-922: a lowercase particle name with an 'i' resolves the same under the Turkish default locale")
	void resolveParticle_lowercaseNameWithI_ignoresDefaultLocale() throws ReflectiveOperationException {
		Method resolve = WantedHud.class.getDeclaredMethod("resolveParticle", String.class);
		resolve.setAccessible(true);

		Particle crit = (Particle) resolve.invoke(null, "crit");

		assertEquals(XParticle.CRIT.get(), crit, "crit must not fall back to the red dust default");
	}

}
