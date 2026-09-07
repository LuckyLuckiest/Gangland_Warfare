package org.luckyraven.gangland.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.luckyraven.keystone.exception.PluginException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DamageData implements Cloneable {

	private double explosionDamage;
	/**
	 * AOE blast radius in blocks. Authored separately from {@link #explosionDamage}: the two were conflated before
	 * 0.8.3, which turned a {@code Explosion_Damage: 50} rocket into a 50-block-radius blast.
	 */
	private double explosionRadius;
	private int    fireTicks;
	private double headDamage;
	private int    criticalHitChance;
	private double criticalHitDamage;

	@Override
	public DamageData clone() {
		try {
			return (DamageData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
