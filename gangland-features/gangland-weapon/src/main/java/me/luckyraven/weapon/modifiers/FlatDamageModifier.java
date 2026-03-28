package me.luckyraven.weapon.modifiers;

/**
 * Adds a flat bonus to the projectile's damage, applied after all other modifiers.
 *
 * @param bonus Amount of extra damage to add (must be > 0)
 */
public record FlatDamageModifier(double bonus) { }
