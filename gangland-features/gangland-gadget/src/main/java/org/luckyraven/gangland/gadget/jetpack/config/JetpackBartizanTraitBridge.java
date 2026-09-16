package org.luckyraven.gangland.gadget.jetpack.config;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.bartizan.api.wearable.Wearable;

import java.util.Map;

/**
 * WS7-D4 static bridge: registers a jetpack's damage-reduction/trait numbers with Bartizan's
 * {@code WearableCatalog} as an <em>external</em> entry (Bartizan never builds/converts/gives/serialises it —
 * see {@code brainstorming/decoupling-wave-2026-09-14/exec/WS7/D4-design.md}). Gadget owns the item entirely;
 * Bartizan's only role is reading this registered definition to compute a damage reduction.
 *
 * <p>Same shape as the B5-ruled {@code CarMeleeWeaponLookup}: a <b>static</b> helper — no {@code @Bean}, no
 * field, no constructor parameter, not in any {@code @AutowireTarget}. This class (and its Bartizan-typed method
 * signature) is only linked when {@link #register} is actually called, and the only call site is inside
 * {@code JetpackAddon}'s {@code Settings.isBartizanAvailable()} guard — so a Bartizan-less server never loads
 * this class at all.
 */
public final class JetpackBartizanTraitBridge {

	private JetpackBartizanTraitBridge() {
	}

	/**
	 * @return {@code true} once the definition was actually registered with Bartizan's {@code WearableCatalog};
	 *         {@code false} when {@code BartizanApi} is not on the {@code ServicesManager} (the early return) — the
	 *         caller uses this to decide whether it is safe to stamp the {@code wearable} tag (I1 review fix).
	 */
	public static boolean register(String key, double baseDamageReduction, Map<String, Integer> traits) {
		RegisteredServiceProvider<BartizanApi> rsp = Bukkit.getServicesManager().getRegistration(BartizanApi.class);
		if (rsp == null) return false;

		Wearable wearable = Wearable.builder()
		                            .wearableKey(key)
		                            .baseDamageReduction(baseDamageReduction)
		                            .traits(traits)
		                            .external(true)
		                            .build();

		rsp.getProvider().wearables().register(key, wearable);
		return true;
	}
}
