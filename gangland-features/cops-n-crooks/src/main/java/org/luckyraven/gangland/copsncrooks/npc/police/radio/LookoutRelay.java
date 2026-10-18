package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.data.gang.GangMembership;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;

/**
 * Whether {@code hearer} relays the police radio he hears to {@code hunted}: an unwanted gang mate. Installed on
 * {@link CopRadio#setLookout}. @since 0.20.0
 */
public class LookoutRelay implements BiPredicate<Player, Player> {

	private final BooleanSupplier       enabled;
	private final ToIntFunction<Player> wantedLevel;
	private final GangMembership        gangs;

	public LookoutRelay(BooleanSupplier enabled, ToIntFunction<Player> wantedLevel, GangMembership gangs) {
		this.enabled     = enabled;
		this.wantedLevel = wantedLevel;
		this.gangs       = gangs;
	}

	@Override
	public boolean test(Player hearer, Player hunted) {
		if (!enabled.getAsBoolean() || hearer.equals(hunted) || wantedLevel.applyAsInt(hearer) != 0) return false;

		int gang = gangs.gangIdOf(hearer.getUniqueId());
		return gang != -1 && gang == gangs.gangIdOf(hunted.getUniqueId());
	}
}
