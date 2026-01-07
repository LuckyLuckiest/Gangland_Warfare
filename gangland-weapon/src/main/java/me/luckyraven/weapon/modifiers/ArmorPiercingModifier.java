package me.luckyraven.weapon.modifiers;

/**
 * Bypasses target armor.
 *
 * @param armorBypass Percentage of armor ignored (0.0 - 1.0)
 */
public record ArmorPiercingModifier(double armorBypass) {

	public double calculateEffectiveArmor(double armor) {
		return armor * (1.0 - armorBypass);
	}

}
