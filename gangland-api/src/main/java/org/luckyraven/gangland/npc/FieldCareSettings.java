package org.luckyraven.gangland.npc;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

/**
 * Field care for a squad of NPCs: a hurt member limps and bleeds, and a medic walks over and patches it up. Sits above
 * {@link RetreatSettings}' threshold, so a member is hurt (and treatable) before it is badly hurt enough to retreat.
 *
 * @param enabled        {@code false}: nobody is ever hurt, so no limp, bleeding or treatment either.
 * @param healthFraction hurt at or below this fraction of max health (0-1).
 * @param limpSpeed      a hurt member's walking speed, as a fraction of its normal speed (0.1-1).
 * @param medicEnabled   {@code false} keeps the hurt state but no medic ever treats anyone.
 * @param medicRadius    blocks a medic answers a hurt squad mate within (2-64).
 * @param healRange      blocks from the patient the medic treats it from (1-6).
 * @param channelTicks   game ticks one treatment takes; a hit on the medic starts it over.
 * @param healFraction   the share of max health one treatment restores (0-1).
 * @param bleed          the blood a hurt member sheds ({@code Hurt} block).
 * @since 0.13.0
 */
public record FieldCareSettings(boolean enabled, double healthFraction, double limpSpeed, boolean medicEnabled,
                                double medicRadius, double healRange, int channelTicks, double healFraction,
                                BleedSettings bleed) {

	public FieldCareSettings(boolean enabled, double healthFraction, double limpSpeed, boolean medicEnabled,
	                         double medicRadius, double healRange, int channelTicks, double healFraction) {
		this(enabled, healthFraction, limpSpeed, medicEnabled, medicRadius, healRange, channelTicks, healFraction,
		     BleedSettings.DEFAULT);
	}

	/** Hurt at half health, limping at 0.7; a medic within 24 blocks heals half of max after 3 s from 2.5 blocks. */
	public static final FieldCareSettings DEFAULT = new FieldCareSettings(true, 0.5, 0.7, true, 24.0, 2.5, 60, 0.5,
	                                                                BleedSettings.DEFAULT);

	/**
	 * Reads {@code Enabled}, {@code Health_Fraction}, {@code Limp_Speed}, {@code Medic_Enabled}, {@code Medic_Radius},
	 * {@code Heal_Range}, {@code Channel_Ticks} and {@code Heal_Fraction} and the {@code Hurt} block ({@link BleedSettings}) from {@code node} over {@code defaults} key
	 * by key; out-of-range values are clamped and reported at the key's line.
	 */
	public static FieldCareSettings read(@Nullable NodeReader node, ConfigReport report, FieldCareSettings defaults) {
		if (node == null) return defaults;
		MappingNode hurt = node.get("Hurt").asMapping().orNull();
		return new FieldCareSettings(
				node.get("Enabled").asBool().orDefault(defaults.enabled()),
				TacticsConfig.readClampedDouble(node, report, "Health_Fraction", defaults.healthFraction(), 0, 1),
				TacticsConfig.readClampedDouble(node, report, "Limp_Speed", defaults.limpSpeed(), 0.1, 1),
				node.get("Medic_Enabled").asBool().orDefault(defaults.medicEnabled()),
				TacticsConfig.readClampedDouble(node, report, "Medic_Radius", defaults.medicRadius(), 2, 64),
				TacticsConfig.readClampedDouble(node, report, "Heal_Range", defaults.healRange(), 1, 6),
				TacticsConfig.readClampedInt(node, report, "Channel_Ticks", defaults.channelTicks(), 1, 1200),
				TacticsConfig.readClampedDouble(node, report, "Heal_Fraction", defaults.healFraction(), 0, 1),
				BleedSettings.read(hurt != null ? NodeReader.of(hurt, report) : null, report, defaults.bleed()));
	}

	/** Whether an NPC at {@code health} of {@code maxHealth} is hurt now (never with field care off). */
	public boolean isHurt(double health, double maxHealth) {
		return enabled && maxHealth > 0 && health <= maxHealth * healthFraction;
	}
}
