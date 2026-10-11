package org.luckyraven.gangland.fights.fight;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.fights.arena.ArenaPoint;

import java.util.UUID;

/** What a fighter looked like before the fight, to put back afterwards. {@link #restore} is filled by W6-S. */
public record PlayerSnapshot(UUID player, ArenaPoint returnTo, double health, int food) {

	public void restore(Player target) {
	}
}
