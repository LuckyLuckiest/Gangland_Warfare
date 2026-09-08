package org.luckyraven.gangland.civilians.npc.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.keystone.npc.spi.NpcTargetFilter;

/**
 * Wraps {@link DownedPlayerRegistry#isDowned} so a Gangland NPC's {@code NpcTargetFilter} refuses to attack a
 * downed player, exactly as the pre-0.9.0 downed-player gates did. Non-player targets are always attackable — this
 * filter only gates the player case.
 */
public class DownedTargetFilter implements NpcTargetFilter {

	@Override
	public boolean isAttackable(LivingEntity target) {
		if (!(target instanceof Player player)) return true;

		return !DownedPlayerRegistry.isDowned(player.getUniqueId());
	}

}
