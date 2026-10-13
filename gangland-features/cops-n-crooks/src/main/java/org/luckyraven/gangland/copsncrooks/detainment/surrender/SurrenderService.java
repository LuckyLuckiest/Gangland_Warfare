package org.luckyraven.gangland.copsncrooks.detainment.surrender;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.detainment.settings.SurrenderSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;

import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 0.21.0 Surrender: a crouching, empty-handed suspect who is seen at 1 to 3 stars makes the squad hold fire; standing
 * up ends it, a hit inside the resist window is Resisting_Arrest.
 *
 * @since 0.21.0
 */
public class SurrenderService {

	public SurrenderService(Supplier<SurrenderSettings> settings, CopRadio copRadio, WantedMessages messages,
	                        CrimeService crimes, LongSupplier clock, Predicate<Player> restrained) {
	}

	/** AI tick hook: starts, keeps or ends the surrender of {@code player}. */
	public void tick(Player player, @Nullable CopGroup group) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Cop-attacked hook: a hit by a surrendered suspect; runs before the manager escalates the group. */
	public void onCopAttacked(CopNpc cop, Player attacker) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
