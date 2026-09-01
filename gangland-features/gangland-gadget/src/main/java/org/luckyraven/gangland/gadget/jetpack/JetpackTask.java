package org.luckyraven.gangland.gadget.jetpack;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.core.configuration.SoundConfiguration;
import org.luckyraven.keystone.util.ActionBarManager;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.ParticleUtil;
import org.luckyraven.keystone.util.PlayerUtil;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.gadget.fuel.FuelService;
import org.luckyraven.gangland.item.fuel.FuelBar;
import org.luckyraven.gangland.item.wearable.Wearable;
import org.luckyraven.gangland.item.wearable.WearableTrait;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.dto.ScopeData;

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

	private static final int                 SOUND_INTERVAL_TICKS = 10;
	private final        JetpackSession      session;
	private final        JetpackService      jetpackService;
	private final        FuelService         fuelService;
	private final        GadgetPhysicsConfig physicsConfig;
	private final        WeaponService       weaponService;

	private int     thrustTicks         = 0;
	private boolean prevGlideToggleHeld = false;
	private int     soundTick           = 0;

	public JetpackTask(JetpackSession session, JetpackService jetpackService, FuelService fuelService,
	                   GadgetPhysicsConfig physicsConfig, WeaponService weaponService) {
		this.session        = session;
		this.jetpackService = jetpackService;
		this.fuelService    = fuelService;
		this.physicsConfig  = physicsConfig;
		this.weaponService  = weaponService;
	}

	@Override
	public void run() {
		Player   player  = session.getPlayer();
		Wearable jetpack = session.getJetpackWearable();

		if (checkGuards(player, jetpack)) return;

		boolean spaceHeld = session.isInputJump();
		// While a held weapon is scoped, ScopeJumpListener blocks upward Y movement on PlayerMoveEvent — the jump
		// can't happen, so the thrust path must not consume fuel either. Glide (sneak+space) is intentionally left
		// alone since the bug is about the jump key, not the glide gesture.
		if (spaceHeld && isScoped(player)) {
			spaceHeld = false;
		}
		boolean sneakHeld = session.isInputSneak();
		boolean hasFuel   = fuelService.hasFuelOnWearable(player);
		boolean onGround  = PlayerUtil.isOnGround(player);

		handleGlideToggle(spaceHeld, sneakHeld, onGround);

		Vector velocity = player.getVelocity();
		double newY     = applyVerticalPhysics(player, jetpack, velocity.getY(), hasFuel, spaceHeld, onGround);
		applyHorizontalPhysics(player, velocity.getX(), newY, velocity.getZ(), hasFuel, spaceHeld, onGround);
		updateActionBar(player, spaceHeld, hasFuel);
		playFlightSounds(player, jetpack);
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

	private double applyVerticalPhysics(Player player, Wearable jetpack, double currentY, boolean hasFuel,
	                                    boolean spaceHeld, boolean onGround) {
		if (session.isGlideModeActive()) {
			thrustTicks = 0;
			if (hasFuel) {
				fuelService.consumeFuelFromWearable(player, getEffectiveConsumptionRate(jetpack));
				ParticleUtil.spawnJetpackGlide(player);
				session.setThrusting(false);
				session.setGliding(true);
				return 0.0;
			}
			session.setGlideModeActive(false);
		}
		if (hasFuel && spaceHeld && !onGround) {
			fuelService.consumeFuelFromWearable(player, getEffectiveConsumptionRate(jetpack));
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

	private void applyHorizontalPhysics(Player player, double newX, double newY, double newZ, boolean hasFuel,
	                                    boolean spaceHeld, boolean onGround) {
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

	private void updateActionBar(Player player, boolean spaceHeld, boolean hasFuel) {
		int    currentFuel = fuelService.getWearableFuelLevel(player);
		int    maxFuel     = fuelService.getWearableMaxFuelLevel(player);
		String actionBar;
		if (session.isGlideModeActive()) {
			actionBar = ChatUtil.color("&b\u2708 Gliding");
		} else if (spaceHeld && !hasFuel) {
			actionBar = ChatUtil.color("&c\u26A0 No fuel \u2014 refuel the jetpack!");
		} else {
			actionBar = FuelBar.render(currentFuel, maxFuel);
		}
		ActionBarManager.sendBackground(player, actionBar, 10);
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

	private void playFlightSounds(Player player, Wearable jetpack) {
		soundTick++;
		if (soundTick < SOUND_INTERVAL_TICKS) return;
		soundTick = 0;

		if (session.isThrusting()) {
			SoundConfiguration.playSounds(player, jetpack.getThrustDefaultSound(), jetpack.getThrustCustomSound());
		} else if (session.isGliding()) {
			SoundConfiguration.playSounds(player, jetpack.getGlideDefaultSound(), jetpack.getGlideCustomSound());
		}
	}

	private boolean isWearingJetpack(Player player, Wearable jetpack) {
		ItemStack chestplate = player.getInventory().getChestplate();
		if (chestplate == null || chestplate.getType().isAir()) return false;
		String key = Wearable.getWearableKey(chestplate);
		return key != null && key.equals(jetpack.getWearableKey());
	}

	private boolean isScoped(Player player) {
		ItemStack held = player.getInventory().getItemInMainHand();
		if (held.getType().isAir()) return false;
		Weapon weapon = weaponService.validateAndGetWeapon(player, held);
		if (weapon == null) return false;
		ScopeData scope = weapon.getScopeData();
		return scope != null && scope.isScoped();
	}

}
