package org.luckyraven.gangland.gadget.jetpack;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.ActionBarManager;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.ParticleUtil;
import org.luckyraven.keystone.util.PlayerUtil;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.gangland.item.fuel.FuelBar;

import java.util.Map;

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

	private int     thrustTicks         = 0;
	private boolean prevGlideToggleHeld = false;
	private int     soundTick           = 0;

	public JetpackTask(JetpackSession session, JetpackService jetpackService, FuelService fuelService,
	                   GadgetPhysicsConfig physicsConfig) {
		this.session        = session;
		this.jetpackService = jetpackService;
		this.fuelService    = fuelService;
		this.physicsConfig  = physicsConfig;
	}

	@Override
	public void run() {
		Player  player  = session.getPlayer();
		Jetpack jetpack = session.getJetpack();

		if (checkGuards(player, jetpack)) return;

		boolean spaceHeld = session.isInputJump();
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

	private boolean checkGuards(Player player, Jetpack jetpack) {
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

	private double applyVerticalPhysics(Player player, Jetpack jetpack, double currentY, boolean hasFuel,
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

	private int getEffectiveConsumptionRate(Jetpack jetpack) {
		return jetpack.getFuelConsumptionRate();
	}

	private void playFlightSounds(Player player, Jetpack jetpack) {
		soundTick++;
		if (soundTick < SOUND_INTERVAL_TICKS) return;
		soundTick = 0;

		if (session.isThrusting()) {
			SoundEffect.playSounds(player, soundTag(jetpack, SoundEffect.SoundType.VANILLA, "Thrust", "Default_Sound"),
			                       soundTag(jetpack, SoundEffect.SoundType.CUSTOM, "Thrust", "Custom_Sound"));
		} else if (session.isGliding()) {
			SoundEffect.playSounds(player, soundTag(jetpack, SoundEffect.SoundType.VANILLA, "Glide", "Default_Sound"),
			                       soundTag(jetpack, SoundEffect.SoundType.CUSTOM, "Glide", "Custom_Sound"));
		}
	}

	private boolean isWearingJetpack(Player player, Jetpack jetpack) {
		String id = Jetpack.getJetpackId(player.getInventory().getChestplate());
		return id != null && id.equals(jetpack.getJetpackId());
	}

	// ── Jetpack config readers ───────────────────────────────────────────────

	/**
	 * Reads {@code Sounds.<group>.<leaf>} (e.g. {@code Sounds.Thrust.Default_Sound}) — itself a nested
	 * {@code {Sound, Volume, Pitch}} map, parsed by {@code JetpackAddon.loadJetpacks} straight off {@code
	 * items/jetpacks.yml}'s {@code Sounds:} block. Returns {@code null} when any level of the path is absent.
	 */
	@Nullable
	private static SoundEffect soundTag(Jetpack jetpack, SoundEffect.SoundType type, String group, String leaf) {
		Map<String, Object> sounds = jetpack.getSounds();
		if (sounds == null) return null;
		if (!(sounds.get(group) instanceof Map<?, ?> groupMap)) return null;
		if (!(groupMap.get(leaf) instanceof Map<?, ?> leafMap)) return null;

		if (!(leafMap.get("Sound") instanceof String sound) || sound.isEmpty()) return null;
		float volume = leafMap.get("Volume") instanceof Number n ? n.floatValue() : 1.0f;
		float pitch  = leafMap.get("Pitch") instanceof Number n ? n.floatValue() : 1.0f;
		return new SoundEffect(type, sound, volume, pitch);
	}

}
