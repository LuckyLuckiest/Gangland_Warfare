package org.luckyraven.gangland.gadget.jetpack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
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
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.bartizan.api.wearable.Wearable;
import org.luckyraven.bartizan.api.weapon.Weapon;
import org.luckyraven.bartizan.api.weapon.WeaponCatalog;
import org.luckyraven.bartizan.api.weapon.dto.ScopeData;

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
			return Math.min(currentY + extraDouble(jetpack, "jetpack_ascend_power", 0) * (0.1 + 0.9 * ramp),
			                extraDouble(jetpack, "jetpack_max_speed_y", currentY));
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
		int baseRate = extraInt(jetpack, "jetpack_fuel_consumption_rate", 0);

		int fuelEfficientLevel = jetpack.traitLevel("fuel_efficient");
		if (fuelEfficientLevel <= 0) return baseRate;

		// fuel_efficient: max level 2, 10% reduction per level — ported verbatim from Bartizan's own
		// Wearable.TRAIT_TABLE ("fuel_efficient", {2, 0.10}); Bartizan owns the trait table now, Gangland only reads
		// the level back through traitLevel(String).
		int    capped    = Math.min(fuelEfficientLevel, 2);
		double reduction = capped * 0.10;
		return Math.max(1, (int) (baseRate * (1.0 - reduction)));
	}

	private void playFlightSounds(Player player, Wearable jetpack) {
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

	private boolean isWearingJetpack(Player player, Wearable jetpack) {
		ItemStack chestplate = player.getInventory().getChestplate();
		if (chestplate == null || chestplate.getType().isAir()) return false;
		String key = Wearable.getWearableKey(chestplate);
		return key != null && key.equals(jetpack.getWearableKey());
	}

	private boolean isScoped(Player player) {
		ItemStack held = player.getInventory().getItemInMainHand();
		if (held.getType().isAir()) return false;

		RegisteredServiceProvider<BartizanApi> rsp = Bukkit.getServicesManager().getRegistration(BartizanApi.class);
		if (rsp == null) return false;
		WeaponCatalog weapons = rsp.getProvider().weapons();

		Weapon weapon = weapons.validateAndGetWeapon(player, held);
		if (weapon == null) return false;
		ScopeData scope = weapon.getScopeData();
		return scope != null && scope.isScoped();
	}

	// ── Wearable.extraTags() readers ─────────────────────────────────────────
	// Jetpack-specific data has no dedicated Wearable fields any more (P2 Q2) — every value below is read from the
	// generic Extra_Tags: map wearables.yml stamps onto the wearable (top-level scalars: NBT-stamped and readable
	// here identically; the nested Sounds map: readable here only, never stamped as NBT).

	private static double extraDouble(Wearable wearable, String key, double fallback) {
		Object value = wearable.extraTags().get(key);
		return value instanceof Number number ? number.doubleValue() : fallback;
	}

	private static int extraInt(Wearable wearable, String key, int fallback) {
		Object value = wearable.extraTags().get(key);
		return value instanceof Number number ? number.intValue() : fallback;
	}

	/**
	 * Reads {@code Sounds.<group>.<leaf>} (e.g. {@code Sounds.Thrust.Default_Sound}) — itself a nested
	 * {@code {Sound, Volume, Pitch}} map, the same shape the deleted {@code WearableAddon.parseSoundConfig} used to
	 * parse into a {@link SoundEffect} at load time. Returns {@code null} when any level of the path is absent.
	 */
	@Nullable
	private static SoundEffect soundTag(Wearable wearable, SoundEffect.SoundType type, String group, String leaf) {
		if (!(wearable.extraTags().get("Sounds") instanceof Map<?, ?> sounds)) return null;
		if (!(sounds.get(group) instanceof Map<?, ?> groupMap)) return null;
		if (!(groupMap.get(leaf) instanceof Map<?, ?> leafMap)) return null;

		if (!(leafMap.get("Sound") instanceof String sound) || sound.isEmpty()) return null;
		float volume = leafMap.get("Volume") instanceof Number n ? n.floatValue() : 1.0f;
		float pitch  = leafMap.get("Pitch") instanceof Number n ? n.floatValue() : 1.0f;
		return new SoundEffect(type, sound, volume, pitch);
	}

}
