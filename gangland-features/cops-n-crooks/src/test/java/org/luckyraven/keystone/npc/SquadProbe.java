package org.luckyraven.keystone.npc;

import org.bukkit.entity.LivingEntity;

/**
 * Test-only window on {@link NpcSquad}'s package-private eyes-on marks (Keystone 1.15.4): whether a squad member
 * without its own sight would be let chase {@code target}, i.e. whether some NPC reported through
 * {@code reportSighting(AbstractNpc, Location[, range])} still sees it.
 */
public final class SquadProbe {

	private SquadProbe() {
	}

	public static boolean eyesOn(NpcSquad squad, LivingEntity target) {
		return squad.anyEyesOn(target);
	}
}
