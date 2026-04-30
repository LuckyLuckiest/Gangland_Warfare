package org.luckyraven.gangland.weapon.util;

import com.cryptomorin.xseries.XPotion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.luckyraven.gangland.weapon.types.biological.BiologicalAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses potion effect tokens of the form {@code EFFECT-duration[-amplifier]} (e.g. {@code BLINDNESS-100-1}).
 *
 * <p>Used by both {@link BiologicalAction} and the throwable smoke/stun
 * subtypes so the token grammar stays consistent across the weapon system.
 *
 * <p>The {@code amplifier} field in the token is 1-indexed (matches the in-game potion-tier display) and is
 * converted to Bukkit's 0-indexed amplifier internally. Missing amplifiers default to 0 (tier I).
 */
public final class PotionEffectParser {

	private PotionEffectParser() {
	}

	/**
	 * Parses a comma-separated list of effect tokens. Invalid tokens are silently skipped — caller is responsible for
	 * authoring valid yml.
	 */
	public static List<PotionEffect> parseList(List<String> tokens) {
		List<PotionEffect> effects = new ArrayList<>();
		if (tokens == null || tokens.isEmpty()) return effects;

		for (String entry : tokens) {
			if (entry == null || entry.isBlank()) continue;
			for (String token : entry.split(",")) {
				PotionEffect effect = parseSingle(token);
				if (effect != null) effects.add(effect);
			}
		}
		return effects;
	}

	/**
	 * Parses one {@code EFFECT-duration[-amplifier]} token. Returns {@code null} if the token is malformed or names an
	 * unknown effect.
	 */
	public static PotionEffect parseSingle(String token) {
		if (token == null) return null;
		String[] parts = token.trim().split("-");
		if (parts.length < 2) return null;

		PotionEffectType type = XPotion.of(parts[0].trim()).map(XPotion::getPotionEffectType).orElse(null);
		if (type == null) return null;

		try {
			int duration  = Integer.parseInt(parts[1].trim());
			int amplifier = parts.length >= 3 ? Math.max(0, Integer.parseInt(parts[2].trim()) - 1) : 0;
			return new PotionEffect(type, duration, amplifier);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

}
