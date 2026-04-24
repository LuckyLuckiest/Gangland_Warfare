package me.luckyraven.turf.powerups;

import java.math.BigDecimal;

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
 */
public record PowerupDefinition(String id,
                                String displayName,
                                BigDecimal cost,
                                EffectType effectType,
                                double magnitude,
                                int durationSeconds) {
}
