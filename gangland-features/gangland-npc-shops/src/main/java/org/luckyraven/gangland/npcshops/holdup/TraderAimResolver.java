package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.npcshops.trader.TraderManager;

/**
 * Finds the trader a player is aiming at: same world, alive and spawned, within Range, inside the Aim_Angle cone and
 * in line of sight; the nearest one wins. TDD skeleton: behaviour lands in the green wave.
 */
public final class TraderAimResolver {

	private final TraderManager traders;

	public TraderAimResolver(TraderManager traders) {
		this.traders = traders;
	}

	@Nullable
	public HoldUpTarget resolve(Player player, HoldUpSettings settings) {
		throw new UnsupportedOperationException("TDD stub");
	}

}
