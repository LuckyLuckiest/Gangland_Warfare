package org.luckyraven.gangland.copsncrooks.npc.police.standoff;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PerimeterController;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;

import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The barricade standoff (0.21.0): when the squad cannot reach a suspect it posts a ring, takes cover, holds the refill,
 * negotiates on the radio and raises a star at the timeout. TDD skeleton: no behaviour yet.
 *
 * @since 0.21.0
 */
@CustomLog
public class StandoffController {

	public StandoffController(Supplier<CopConfigProvider> provider, CopRadio radio, PerimeterController perimeter,
	                          WantedStars stars, UserManager<Player> users, Predicate<Player> restrained,
	                          LongSupplier clock) {
	}

	/** AI tick hook: starts, runs (negotiate, timeout) or ends the suspect's standoff. */
	public void tick(Player player, @Nullable CopGroup group) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Cop-attacked hook: the suspect hit a cop, so his standoff ends (ESCALATE). */
	public void onCopAttacked(CopNpc cop, Player attacker) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** A cop of the suspect's squad died at {@code where}; feeds the man-down trigger. */
	public void recordCasualty(UUID suspectId, Location where) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public boolean isActive(UUID suspectId) {
		return false;
	}
}
