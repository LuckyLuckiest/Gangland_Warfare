package org.luckyraven.gangland.turf.npc.guard;

/**
 * Knobs for the turf cop guard: whether turf defenders answer cops that hit a protected player, how far from the cop
 * a defender or Quartermaster still engages, and whether allied gangs count as protected.
 *
 * @param enabled whether defenders answer cops at all
 * @param radius blocks from the cop within which a defender or Quartermaster engages it
 * @param includeAllies whether members of an allied gang are protected alongside the turf owner's members
 */
public record CopGuardConfig(boolean enabled, double radius, boolean includeAllies) {
}
