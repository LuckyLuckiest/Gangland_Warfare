package org.luckyraven.gangland.civilians.npc.combat;

import org.bukkit.entity.Player;
import org.luckyraven.bartizan.api.combat.CombatEligibility;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;

/**
 * The wave's one direction reversal: Gangland <strong>publishes</strong> this on the {@code ServicesManager} (as
 * the interface type, not this concrete class — {@code @Bean(publishToServicesManager = true)} registers under the
 * declared return type) and Bartizan pulls it lazily on every hit check.
 *
 * <p>{@link #canBeHit} is the <em>inverse</em> of {@link DownedPlayerRegistry#isDowned}: a downed player must not be
 * shootable. Get the polarity backwards and every downed player becomes hittable with no test and no log line
 * (R1 B4) — {@code GanglandCombatEligibilityTest} pins both directions.
 */
public class GanglandCombatEligibility implements CombatEligibility {

	@Override
	public boolean canBeHit(Player player) {
		return !DownedPlayerRegistry.isDowned(player.getUniqueId());
	}

}
