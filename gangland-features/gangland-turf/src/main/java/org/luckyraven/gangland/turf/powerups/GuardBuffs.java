package org.luckyraven.gangland.turf.powerups;

import com.cryptomorin.xseries.XAttribute;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The pure rules behind the three guard powerups, kept apart so each is tested alone. A buff magnitude is read from
 * YAML and from the stacked product of running buffs, so {@code up} and {@code wait} treat a non-finite or out-of-range
 * value as "no buff" (1.0); {@code applyHealth} and {@code scale} expect an already clamped multiplier.
 */
public final class GuardBuffs {

	/** Vanilla's ceiling on a max-health attribute. */
	private static final double HEALTH_CAP = 1024.0;

	private GuardBuffs() {
	}

	/** A multiplier that grows a number: non-finite or below 1 gives 1.0, else at most {@code max}. */
	public static double up(double m, double max) {
		if (!Double.isFinite(m) || m < 1.0) {
			return 1.0;
		}
		return Math.min(m, max);
	}

	/** A multiplier that shrinks a wait: non-finite, not above 0 or above 1 gives 1.0, else at least {@code min}. */
	public static double wait(double m, double min) {
		if (!Double.isFinite(m) || m <= 0.0 || m > 1.0) {
			return 1.0;
		}
		return Math.max(m, min);
	}

	public static int scale(int base, double m) {
		return Math.max(1, (int) Math.min(Integer.MAX_VALUE, Math.round(base * m)));
	}

	/**
	 * The price of one guard: {@code baseCost} times the garrison_discount {@code multiplier}, kept between 10 % and 100 %
	 * of {@code baseCost} (a non-finite multiplier counts as 1.0), rounded half up and never below 1 (T-275).
	 */
	public static BigDecimal guardPrice(double multiplier, BigDecimal baseCost) {
		// T-275: stacked or mis-set discounts stay between 10 % and 100 % of Cost_Per_Guard, and a guard never costs 0.
		// ponytail: fixed 0.1 floor; make it a turf_powerups.yml knob if owners want cheaper guards.
		multiplier = Double.isFinite(multiplier) ? Math.max(0.1, Math.min(1.0, multiplier)) : 1.0;
		BigDecimal price = baseCost.multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.HALF_UP);
		return price.max(BigDecimal.ONE);
	}

	/** The MAX_HEALTH base value, or -1 with no attribute. */
	public static double maxHealth(LivingEntity e) {
		AttributeInstance attribute = attribute(e);
		return attribute == null ? -1 : attribute.getBaseValue();
	}

	/**
	 * Sets max health to {@code min(1024, base * m)} and scales current health by the same share, never below 1 and
	 * never above the new maximum, so ending a buff cannot kill a hurt guard. A no-op when the maximum is unchanged or
	 * there is no attribute.
	 */
	public static void applyHealth(LivingEntity e, double base, double m) {
		AttributeInstance attribute = attribute(e);
		if (attribute == null) {
			return;
		}
		double oldMax = attribute.getBaseValue();
		double max    = Math.min(HEALTH_CAP, base * m);
		if (max == oldMax) {
			return;
		}
		double health = oldMax > 0 ? e.getHealth() * max / oldMax : max;
		attribute.setBaseValue(max);
		e.setHealth(Math.min(max, Math.max(1.0, health)));
	}

	private static AttributeInstance attribute(LivingEntity e) {
		Attribute type = XAttribute.MAX_HEALTH.get();
		return type == null ? null : e.getAttribute(type);
	}
}
