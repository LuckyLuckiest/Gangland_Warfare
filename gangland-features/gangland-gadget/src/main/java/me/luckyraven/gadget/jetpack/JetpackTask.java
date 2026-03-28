package me.luckyraven.gadget.jetpack;

import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.item.fuel.FuelBar;
import me.luckyraven.item.wearable.Wearable;
import me.luckyraven.item.wearable.WearableTrait;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.util.utilities.ParticleUtil;
import me.luckyraven.util.utilities.PlayerUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Tick-based handler for an active jetpack. Runs every server tick while the jetpack is active.
 *
 * <p>Behavior per tick:
 * <ol>
 *   <li>Guard: verify player is online and still wearing the jetpack</li>
 *   <li>Detect spacebar via {@link Player#isFlying()} (re-cancelled each tick)</li>
 *   <li><b>Thrust mode</b>: space held + has fuel → consume fuel, apply upward velocity, spawn flame particles</li>
 *   <li><b>Glide mode</b>: airborne + no fuel → slow descent with smoke particles</li>
 *   <li><b>Landed</b>: on ground, stop thrusting/gliding</li>
 *   <li>Display fuel on action bar</li>
 * </ol>
 */
public class JetpackTask extends BukkitRunnable {

	private final JetpackSession session;
	private final JetpackService jetpackService;
	private final FuelService    fuelService;

	public JetpackTask(JetpackSession session, JetpackService jetpackService, FuelService fuelService) {
		this.session        = session;
		this.jetpackService = jetpackService;
		this.fuelService    = fuelService;
	}

	@Override
	public void run() {
		Player   player  = session.getPlayer();
		Wearable jetpack = session.getJetpackWearable();

		// Guard: player offline
		if (!player.isOnline()) {
			jetpackService.deactivate(player);
			cancel();
			return;
		}

		// Guard: still wearing the jetpack
		if (!isWearingJetpack(player, jetpack)) {
			jetpackService.deactivate(player);
			cancel();
			return;
		}

		// Detect spacebar: player.isFlying() becomes true when space is held
		// We cancel vanilla flight immediately each tick
		boolean holdingSpace = player.isFlying();
		if (holdingSpace) {
			player.setFlying(false);
		}

		String  fuelKey = jetpack.getFuelKey();
		boolean hasFuel = fuelService.hasFuel(player, fuelKey);

		if (holdingSpace && hasFuel) {
			// === THRUST MODE ===
			int consumeRate = getEffectiveConsumptionRate(jetpack);
			fuelService.consumeFuel(player, fuelKey, consumeRate);

			Vector velocity = player.getVelocity();
			double newY     = Math.min(velocity.getY() + jetpack.getAscendPower(), jetpack.getMaxSpeedY());
			player.setVelocity(velocity.setY(newY));

			ParticleUtil.spawnJetpackFlame(player);

			session.setThrusting(true);
			session.setGliding(false);

		} else if (!PlayerUtil.isOnGround(player) && !hasFuel) {
			// === GLIDE MODE ===
			Vector velocity = player.getVelocity();
			double glideY   = jetpack.getGlideDescentRate();
			player.setVelocity(velocity.setY(Math.max(velocity.getY(), glideY)));

			ParticleUtil.spawnJetpackGlide(player);

			session.setThrusting(false);
			session.setGliding(true);

		} else if (PlayerUtil.isOnGround(player)) {
			// === LANDED ===
			session.setThrusting(false);
			session.setGliding(false);
		}

		// Display fuel on action bar
		int currentFuel = fuelService.getFuelLevel(player, fuelKey);
		int maxFuel     = fuelService.getMaxFuelLevel(player, fuelKey);
		ChatUtil.sendActionBar(player, FuelBar.render(currentFuel, maxFuel));
	}

	/**
	 * Calculates the effective fuel consumption rate, accounting for the {@link WearableTrait#FUEL_EFFICIENT} trait.
	 */
	private int getEffectiveConsumptionRate(Wearable jetpack) {
		int baseRate = jetpack.getFuelConsumptionRate();
		if (jetpack.getTraits() == null) return baseRate;

		Integer fuelEfficientLevel = jetpack.getTraits().get(WearableTrait.FUEL_EFFICIENT);
		if (fuelEfficientLevel == null || fuelEfficientLevel <= 0) return baseRate;

		int    capped    = Math.min(fuelEfficientLevel, WearableTrait.FUEL_EFFICIENT.getMaxLevel());
		double reduction = capped * WearableTrait.FUEL_EFFICIENT.getEffectPerLevel();
		return Math.max(1, (int) (baseRate * (1.0 - reduction)));
	}

	private boolean isWearingJetpack(Player player, Wearable jetpack) {
		ItemStack chestplate = player.getInventory().getChestplate();
		if (chestplate == null || chestplate.getType().isAir()) return false;
		String key = Wearable.getWearableKey(chestplate);
		return key != null && key.equals(jetpack.getWearableKey());
	}

}
