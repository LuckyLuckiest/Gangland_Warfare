package org.luckyraven.gangland.civilians.witness;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;

import java.util.function.Predicate;

/**
 * {@link WitnessService.Aim}: the criminal is armed ({@code armed}), within range blocks of the caller and looking at
 * the caller to within angleDegrees. Pure geometry; the armed test is injected so this class never names Bartizan.
 *
 * @since 0.20.0
 */
public final class WitnessAim implements WitnessService.Aim {

	private final Predicate<Player> armed;

	public WitnessAim(Predicate<Player> armed) {
		this.armed = armed;
	}

	@Override
	public boolean aimsAt(Player criminal, CivilianNpc caller, double range, double angleDegrees) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
