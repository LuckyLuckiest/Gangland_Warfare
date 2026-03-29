package me.luckyraven.gadget.jetpack;

import me.luckyraven.gadget.config.GadgetPhysicsConfig;
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
 * <p>Vertical physics:
 * <ul>
 *   <li><b>Thrust</b> (jump held, has fuel): upward delta ramps from 10 % to 100 % of
 *       {@code ascendPower} over {@code thrustRampTicks} ticks, then caps at {@code maxSpeedY}.</li>
 *   <li><b>Descent</b> (airborne, no thrust): descent acceleration subtracted per tick so descent
 *       gradually speeds up.</li>
 *   <li><b>Landed</b>: no velocity override; Minecraft ground physics applies.</li>
 * </ul>
 *
 * <p>Horizontal physics: while airborne, WASD input applies a configured influence per tick in
 * the player's look direction, capped at the configured max horizontal speed. Vanilla air drag
 * decelerates the player when no key is held.
 *
 * <p>All physics constants are sourced from {@link GadgetPhysicsConfig}.
 */
public class JetpackTask extends BukkitRunnable {

	private final JetpackSession      session;
	private final JetpackService      jetpackService;
	private final FuelService         fuelService;
	private final GadgetPhysicsConfig physicsConfig;

	/**
	 * Consecutive ticks of active thrust; drives the ramp-up curve.
	 */
	private int     thrustTicks         = 0;
	/**
	 * Previous tick's state of shift+jump held, used for rising-edge detection of the glide toggle.
	 */
	private boolean prevGlideToggleHeld = false;

	public JetpackTask(JetpackSession session, JetpackService jetpackService, FuelService fuelService,
	                   GadgetPhysicsConfig physicsConfig) {
		this.session        = session;
		this.jetpackService = jetpackService;
		this.fuelService    = fuelService;
		this.physicsConfig  = physicsConfig;
	}

	@Override
	public void run() {
		Player   player  = session.getPlayer();
		Wearable jetpack = session.getJetpackWearable();

		if (checkGuards(player, jetpack)) return;

		boolean spaceHeld = session.isInputJump();
		boolean sneakHeld = session.isInputSneak();
		String  fuelKey   = jetpack.getFuelKey();
		boolean hasFuel   = fuelService.hasFuel(player, fuelKey);
		boolean onGround  = PlayerUtil.isOnGround(player);

		handleGlideToggle(spaceHeld, sneakHeld, onGround);

		Vector velocity = player.getVelocity();
		double newY     = applyVerticalPhysics(player, jetpack, fuelKey, velocity.getY(), hasFuel, spaceHeld, onGround);
		applyHorizontalPhysics(player, velocity.getX(), newY, velocity.getZ(), hasFuel, spaceHeld, onGround);
		updateActionBar(player, fuelKey, spaceHeld, hasFuel);
	}

	private boolean checkGuards(Player player, Wearable jetpack) {
		if (!player.isOnline()) {
			jetpackService.deactivate(player);
			cancel();
			return true;
		}
		if (!isWearingJetpack(player, jetpack)) {
			jetpackService.deactivate(player);
			cancel();
			return true;
		}
		return false;
	}

	private void handleGlideToggle(boolean spaceHeld, boolean sneakHeld, boolean onGround) {
		boolean glideCombo = sneakHeld && spaceHeld;
		if (glideCombo && !prevGlideToggleHeld && !onGround) {
			session.setGlideModeActive(!session.isGlideModeActive());
		}
		prevGlideToggleHeld = glideCombo;
		if (onGround || (spaceHeld && !sneakHeld)) {
			session.setGlideModeActive(false);
		}
	}

	private double applyVerticalPhysics(Player player, Wearable jetpack, String fuelKey, double currentY,
	                                    boolean hasFuel, boolean spaceHeld, boolean onGround) {
		if (session.isGlideModeActive()) {
			thrustTicks = 0;
			ParticleUtil.spawnJetpackGlide(player);
			session.setThrusting(false);
			session.setGliding(true);
			return 0.0;
		}
		if (hasFuel && spaceHeld) {
			fuelService.consumeFuel(player, fuelKey, getEffectiveConsumptionRate(jetpack));
			thrustTicks++;
			double ramp = Math.min(thrustTicks / (double) physicsConfig.getJetpackThrustRampTicks(), 1.0);
			ParticleUtil.spawnJetpackFlame(player);
			session.setThrusting(true);
			session.setGliding(false);
			return Math.min(currentY + jetpack.getAscendPower() * (0.1 + 0.9 * ramp), jetpack.getMaxSpeedY());
		}
		if (!onGround) {
			thrustTicks = 0;
			ParticleUtil.spawnJetpackGlide(player);
			session.setThrusting(false);
			session.setGliding(true);
			return Math.max(currentY - physicsConfig.getJetpackDescentAccel(),
			                physicsConfig.getJetpackMaxDescentSpeed());
		}
		thrustTicks = 0;
		session.setThrusting(false);
		session.setGliding(false);
		return currentY;
	}

	private void applyHorizontalPhysics(Player player, double newX, double newY, double newZ,
	                                    boolean hasFuel, boolean spaceHeld, boolean onGround) {
		if (onGround && !(hasFuel && spaceHeld)) return;

		boolean fwd = session.isInputForward();
		boolean bwd = session.isInputBackward();
		boolean lft = session.isInputLeft();
		boolean rgt = session.isInputRight();

		if (fwd || bwd || lft || rgt) {
			double yaw = Math.toRadians(player.getLocation().getYaw());
			double dx  = 0, dz = 0;
			if (fwd) {
				dx -= Math.sin(yaw);
				dz += Math.cos(yaw);
			}
			if (bwd) {
				dx += Math.sin(yaw);
				dz -= Math.cos(yaw);
			}
			if (lft) {
				dx += Math.cos(yaw);
				dz += Math.sin(yaw);
			}
			if (rgt) {
				dx -= Math.cos(yaw);
				dz -= Math.sin(yaw);
			}

			double len = Math.sqrt(dx * dx + dz * dz);
			if (len > 0) {
				dx /= len;
				dz /= len;
			}

			newX += dx * physicsConfig.getJetpackHorizInfluence();
			newZ += dz * physicsConfig.getJetpackHorizInfluence();

			double horizSpeed = Math.sqrt(newX * newX + newZ * newZ);
			if (horizSpeed > physicsConfig.getJetpackMaxHorizSpeed()) {
				newX = newX / horizSpeed * physicsConfig.getJetpackMaxHorizSpeed();
				newZ = newZ / horizSpeed * physicsConfig.getJetpackMaxHorizSpeed();
			}
		}

		player.setVelocity(new Vector(newX, newY, newZ));
	}

	private void updateActionBar(Player player, String fuelKey, boolean spaceHeld, boolean hasFuel) {
		int    currentFuel = fuelService.getFuelLevel(player, fuelKey);
		int    maxFuel     = fuelService.getMaxFuelLevel(player, fuelKey);
		String actionBar;
		if (session.isGlideModeActive()) {
			actionBar = ChatUtil.color("&b\u2708 Gliding");
		} else if (spaceHeld && !hasFuel) {
			actionBar = ChatUtil.color("&c\u26A0 No fuel \u2014 refuel the jetpack!");
		} else {
			actionBar = FuelBar.render(currentFuel, maxFuel);
		}
		ChatUtil.sendActionBar(player, actionBar);
	}

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
