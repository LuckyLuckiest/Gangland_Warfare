package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.npcshops.trader.TraderManager;
import org.luckyraven.gangland.npcshops.trader.TraderNpc;

/**
 * Finds the trader a player is aiming at: same world, alive and spawned, within Range, inside the Aim_Angle cone and
 * in line of sight; the nearest one wins.
 */
public final class TraderAimResolver {

	private final TraderManager traders;

	public TraderAimResolver(TraderManager traders) {
		this.traders = traders;
	}

	@Nullable
	public HoldUpTarget resolve(Player player, HoldUpSettings settings) {
		World world = player.getWorld();
		if (world == null) {
			return null;
		}

		Location eye = player.getEyeLocation();
		double[] bestDistance = {Double.MAX_VALUE};
		HoldUpTarget[] best = {null};

		traders.forEach((TraderNpc trader) -> {
			if (!trader.isAlive() || !(trader.getNpc().getEntity() instanceof LivingEntity entity)) {
				return;
			}
			if (!world.equals(entity.getWorld())) {
				return;
			}

			Location target = entity.getEyeLocation();
			if (!HoldUpGeometry.isAimed(eye.getDirection(), eye.toVector(), target.toVector(), settings.range(),
			                            settings.aimAngle()) || !player.hasLineOfSight(entity)) {
				return;
			}

			double distance = eye.distanceSquared(target);
			if (distance < bestDistance[0]) {
				bestDistance[0] = distance;
				String name = trader.getData().getDisplayName();
				best[0] = new HoldUpTarget(trader.getData().getId(), name == null ? "Trader" : name,
				                           entity.getLocation());
			}
		});
		return best[0];
	}

}
