package org.luckyraven.gangland.weapon.modifiers.action;

import org.bukkit.Color;

/**
 * Adds tracer effects to projectiles.
 *
 * @param color Particle color
 * @param glowing Whether projectile glows
 * @param particleSize Size of trail particles
 */
public record TracerModifier(Color color, boolean glowing, float particleSize) { }
