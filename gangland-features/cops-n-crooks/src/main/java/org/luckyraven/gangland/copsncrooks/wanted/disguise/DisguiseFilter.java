package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** The lock-on filter registered on {@code CopPerception}: may this cop pick this player up (0.20.0 lane disguise). */
public class DisguiseFilter implements BiPredicate<CopNpc, Player> {

	public DisguiseFilter(Supplier<DisguiseSettings> settings, SuspectDescriptions descriptions, PoliceOutfit outfit,
	                      ToIntFunction<Player> levelOf, Predicate<Player> freshSighting) {
	}

	public boolean allows(CopNpc cop, Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public boolean test(CopNpc cop, Player player) {
		return allows(cop, player);
	}
}
