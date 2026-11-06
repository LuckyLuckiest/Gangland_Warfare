package org.luckyraven.gangland.turf.powerups;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * A purchasable timed buff entry from {@code turf_powerups.yml}. Definitions are immutable; the live "this buff is
 * running on turf X until Y" record is {@link ActiveTurfBuff}, separate from the catalogue.
 *
 * @param id lowercase lookup id (matches the YAML map key)
 * @param displayName {@code &}-coloured label shown in panels and chat
 * @param cost gang-bank cost to activate one copy of the buff
 * @param effectType what this buff actually changes (see {@link EffectType})
 * @param magnitude per-effect-type magnitude (multiplier / ratio / flat) — the consumer interprets the units
 * @param durationSeconds wall-clock seconds the buff stays active after activation
 * @param icon material name for the panel icon, or null for the default of the effect ({@link #iconMaterial()})
 * @param description {@code &}-coloured lore lines for the panel, empty when the entry has none
 */
public record PowerupDefinition(String id,
                                String displayName,
                                BigDecimal cost,
                                EffectType effectType,
                                double magnitude,
                                int durationSeconds,
                                @Nullable String icon,
                                List<String> description) {

	public PowerupDefinition(String id, String displayName, BigDecimal cost, EffectType effectType, double magnitude,
	                         int durationSeconds) {
		this(id, displayName, cost, effectType, magnitude, durationSeconds, null, List.of());
	}

	/** The configured icon, else the default for the effect. The loader has already checked a configured icon. */
	public Material iconMaterial() {
		if (icon != null) {
			Material material = XMaterial.matchXMaterial(icon).map(XMaterial::get).orElse(null);
			if (material != null) {
				return material;
			}
		}
		return defaultIcon(effectType);
	}

	public static Material defaultIcon(EffectType effectType) {
		return switch (effectType) {
			case INCOME_MULTIPLIER, CAPTURE_DEFENSE_BONUS -> Material.GOLD_INGOT;
			case GARRISON_DISCOUNT -> Material.EMERALD;
			case GUARD_TOUGHNESS -> Material.IRON_BLOCK;
			case BIGGER_GUARD_WAVES -> Material.BELL;
			case FASTER_REINFORCEMENTS -> Material.CLOCK;
		};
	}
}
