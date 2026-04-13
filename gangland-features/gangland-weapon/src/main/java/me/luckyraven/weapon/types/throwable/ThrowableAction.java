package me.luckyraven.weapon.types.throwable;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.utilities.ParticleUtil;
import me.luckyraven.weapon.dto.ThrowableData;
import me.luckyraven.weapon.events.projectile.WeaponRaytraceImpactEvent;
import me.luckyraven.weapon.projectile.ProjectileState;
import me.luckyraven.weapon.util.PotionEffectParser;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ThrowableAction {

	/**
	 * Entity UUIDs currently receiving programmatic explosion damage — used to bypass the event cancel guard. Kept
	 * static so WeaponInteract.onEntityDamage can access it without holding an instance.
	 */
	public static final Set<UUID> pendingDamage = ConcurrentHashMap.newKeySet();

	/**
	 * Maps a victim's UUID to the throwable weapon name that killed them. Consumed by PlayerDeath to set the correct
	 * death message. Kept static so PlayerDeath can access it without holding an instance.
	 */
	public static final Map<UUID, String> pendingKillerWeapon = new ConcurrentHashMap<>();

	/**
	 * Maps a non-living entity's UUID to the configured weapon explosion damage, pre-populated before
	 * {@code World#createExplosion} fires its events. Consumed by {@code CarDamageListener} so vehicles take the
	 * weapon's configured value instead of the vanilla explosion calculation.
	 */
	public static final Map<UUID, Double> pendingVehicleExplosionDamage = new ConcurrentHashMap<>();

	private final JavaPlugin          plugin;
	private final ThrowableWeapon     weapon;
	private final RecoilCompatibility recoilCompatibility;

	public ThrowableAction(JavaPlugin plugin, ThrowableWeapon weapon, RecoilCompatibility recoilCompatibility) {
		this.plugin              = plugin;
		this.weapon              = weapon;
		this.recoilCompatibility = recoilCompatibility;
	}

	public void activate(Player player) {
		ThrowableData data = weapon.getThrowableData();

		if (player.getGameMode() != GameMode.CREATIVE) {
			decrementHeldStack(player);
		}

		World    world  = player.getWorld();
		Location eyeLoc = player.getEyeLocation();

		ItemStack visual = data.getDisplayItem() != null ?
		                   data.getDisplayItem().clone() :
		                   new ItemStack(weapon.getMaterial());
		Item grenade = world.dropItem(eyeLoc, visual);
		grenade.setPickupDelay(Integer.MAX_VALUE);

		Vector throwVec = eyeLoc.getDirection().normalize().multiply(1.2).add(new Vector(0, 0.2, 0));
		grenade.setVelocity(throwVec);

		SoundConfiguration.playSounds(player, weapon.getSoundData().getShotCustom(),
		                              weapon.getSoundData().getShotDefault());

		if (weapon.getRecoilData() != null) {
			weapon.getRecoil().applyRecoil(recoilCompatibility, player);
			weapon.applyPush(player);
		}

		boolean[] wasOnGround       = {false};
		boolean[] stuck             = {false};
		int[]     bounceCount       = {0};
		int[]     bounceCooldown    = {0};
		int[]     tickCount         = {0};
		double[]  prevVelocityLenSq = {throwVec.lengthSquared()};

		RepeatingTimer physicsTimer = new RepeatingTimer(plugin, 1L, time -> {
			if (grenade.isDead()) {
				time.stop();
				return;
			}

			if (stuck[0]) {
				grenade.setVelocity(new Vector(0, 0, 0));
				ParticleUtil.spawnSmokeTrail(grenade.getLocation());
				return;
			}

			tickCount[0]++;
			if (bounceCooldown[0] > 0) bounceCooldown[0]--;
			boolean onGround   = grenade.isOnGround();
			boolean justLanded = onGround && !wasOnGround[0];

			// Detect wall/ceiling collision: item was moving last tick but velocity
			// is now near-zero without having hit the floor (isOnGround is false).
			// Skip the first tick — the item entity's velocity isn't always reflected by
			// getVelocity() immediately after setVelocity() on the very first tick.
			// Also suppress detection for a few ticks after a bounce so the new bounce
			// velocity has time to register and doesn't trigger a false wall-hit.
			Vector curVel   = grenade.getVelocity();
			double curLenSq = curVel.lengthSquared();
			boolean hitSurface = tickCount[0] > 1 && !onGround && curLenSq < 0.005 && prevVelocityLenSq[0] > 0.01 &&
			                     bounceCooldown[0] == 0;
			// For sticky grenades: also catch angled wall hits where the reflected velocity is
			// reduced but not near-zero. If velocity magnitude squared drops to < 40 % of the
			// previous tick's value, a collision absorbed kinetic energy → stick immediately.
			boolean stickyCollision = data.isSticky() && tickCount[0] > 2 && bounceCooldown[0] == 0 &&
			                          prevVelocityLenSq[0] > 0.05 && curLenSq < prevVelocityLenSq[0] * 0.40;
			prevVelocityLenSq[0] = curLenSq;

			if (justLanded || hitSurface || stickyCollision) {
				if (data.isSticky()) {
					stuck[0] = true;
					grenade.setGravity(false);
					grenade.setVelocity(new Vector(0, 0, 0));
				} else if (data.isBounces() && bounceCount[0] < data.getMaxBounces()) {
					bounceCount[0]++;
					double bounceHeight = Math.max(0.15, throwVec.length() * 0.45 * Math.pow(0.65, bounceCount[0]));
					Vector v            = grenade.getVelocity().clone();
					v.setY(bounceHeight);
					v.setX(v.getX() * 0.85);
					v.setZ(v.getZ() * 0.85);
					grenade.setVelocity(v);
					bounceCooldown[0] = 3;
				}
			}

			wasOnGround[0] = onGround;
			ParticleUtil.spawnSmokeTrail(grenade.getLocation());
		});

		physicsTimer.start(false);

		new CountdownTimer(plugin, 0L, 1L, data.getFuseTime(), null, null, timer -> {
			physicsTimer.stop();
			Location blast = grenade.getLocation();
			grenade.remove();
			detonate(blast, data, player, world);
		}).start(false);
	}

	private void decrementHeldStack(Player player) {
		ItemStack held = player.getInventory().getItemInMainHand();

		if (held.getAmount() > 1) {
			held.setAmount(held.getAmount() - 1);
			player.getInventory().setItemInMainHand(held);
			return;
		}

		player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
	}

	private void detonate(Location center, ThrowableData data, Player player, World world) {
		// dispatch on throwable type — only EXPLOSIVE goes through the legacy createExplosion path
		ThrowableType type = data.getType() != null ? data.getType() : ThrowableType.EXPLOSIVE;
		switch (type) {
			case STUN -> {
				detonateStun(center, data, world);
				return;
			}
			case SMOKE -> {
				spawnSmokeCloud(center, data, world);
				return;
			}
			case EXPLOSIVE -> { /* fall through to legacy explosive handler below */ }
		}

		ParticleUtil.spawnExplosionBurst(center);

		double flatBonus = weapon.getModifiersData().hasFlatDamage() ?
		                   weapon.getModifiersData().getFlatDamage().bonus() :
		                   0.0;
		double totalDmg = data.getExplosionDamage() + flatBonus;
		double radiusSq = data.getExplosionRadius() * data.getExplosionRadius();

		// Pre-register weapon damage for non-living entities (vehicles) before the explosion fires its events,
		// so CarDamageListener can use the configured value instead of vanilla explosion damage.
		List<UUID> registeredUuids = new ArrayList<>();
		if (totalDmg > 0) {
			for (Entity nearby : world.getNearbyEntities(center, data.getExplosionRadius(), data.getExplosionRadius(),
			                                             data.getExplosionRadius())) {
				if (nearby instanceof LivingEntity) continue;
				if (nearby.getLocation().distanceSquared(center) > radiusSq) continue;
				pendingVehicleExplosionDamage.put(nearby.getUniqueId(), totalDmg);
				registeredUuids.add(nearby.getUniqueId());
			}
		}

		world.createExplosion(center.getX(), center.getY(), center.getZ(), (float) data.getExplosionRadius(),
		                      false, false, player);

		if (data.getFireTicks() > 0) {
			placeTempFire(center, data.getExplosionRadius(), data.getFireTicks(), world);
		}

		// Clean up entries not consumed by CarDamageListener (e.g. entity out of actual blast range)
		plugin.getServer()
		      .getScheduler()
		      .runTaskLater(plugin, () -> registeredUuids.forEach(pendingVehicleExplosionDamage::remove), 1L);

		ProjectileState explosionState = new ProjectileState(weapon, totalDmg);

		for (Entity nearby : world.getNearbyEntities(center, data.getExplosionRadius(), data.getExplosionRadius(),
		                                             data.getExplosionRadius())) {
			if (!(nearby instanceof LivingEntity target)) continue;
			if (nearby.getLocation().distanceSquared(center) > radiusSq) continue;

			// Fire the unified impact event so future event consumers (and the cops-n-crooks
			// raytrace handlers added in the gangland-weapon raytrace refactor) react to grenade
			// explosions through the same hook as gun shots. The legacy {@code target.damage(...)}
			// call below still drives the existing EntityDamageByEntityEvent path for cops-n-crooks
			// listeners that haven't been migrated.
			WeaponRaytraceImpactEvent impactEvent = new WeaponRaytraceImpactEvent(weapon, player, target, null, null,
			                                                                      target.getLocation(), totalDmg,
			                                                                      explosionState);
			Bukkit.getPluginManager().callEvent(impactEvent);
			if (impactEvent.isCancelled()) continue;

			if (totalDmg > 0) {
				UUID targetUuid = target.getUniqueId();
				pendingDamage.add(targetUuid);
				pendingKillerWeapon.put(targetUuid, weapon.getName());
				target.damage(impactEvent.getDamage(), player);
			}
			if (data.getFireTicks() > 0) target.setFireTicks(data.getFireTicks());
		}

		// getNearbyEntities() never returns the player themselves, so self-damage and knockback must be applied explicitly.
		if (player.getLocation().distanceSquared(center) <= radiusSq) {
			if (totalDmg > 0) player.damage(totalDmg);
			if (data.getFireTicks() > 0) player.setFireTicks(data.getFireTicks());
			Vector blastDir = player.getLocation().toVector().subtract(center.toVector());
			double dist     = blastDir.length();
			if (dist > 0) {
				double strength = (1.0 - dist / data.getExplosionRadius()) * 2.0;
				player.setVelocity(player.getVelocity().add(blastDir.normalize().multiply(strength)));
			} else {
				player.setVelocity(player.getVelocity().add(new Vector(0, 2.0, 0)));
			}
		}
	}

	/**
	 * Scatters short-lived {@link Material#FIRE} blocks in a sphere of {@code radius} around {@code center}. Each fire
	 * block is placed only on top of solid blocks (in an air space) and is automatically removed after
	 * {@code fireTicks} ticks so underlying blocks are never consumed.
	 */
	private void placeTempFire(Location center, double radius, int fireTicks, World world) {
		int                          r        = (int) Math.ceil(radius);
		double                       radiusSq = radius * radius;
		List<org.bukkit.block.Block> placed   = new ArrayList<>();

		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + y * y + z * z > radiusSq) continue;
					org.bukkit.block.Block candidate = world.getBlockAt(
							center.getBlockX() + x,
							center.getBlockY() + y,
							center.getBlockZ() + z);
					if (candidate.getType() != Material.AIR) continue;
					org.bukkit.block.Block below = candidate.getRelative(org.bukkit.block.BlockFace.DOWN);
					if (!below.getType().isSolid()) continue;
					candidate.setType(Material.FIRE);
					placed.add(candidate);
				}
			}
		}

		if (!placed.isEmpty()) {
			plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
				for (org.bukkit.block.Block b : placed) {
					if (b.getType() == Material.FIRE) {
						b.setType(Material.AIR);
					}
				}
			}, fireTicks);
		}
	}

	/**
	 * Stun (flashbang) detonation: no damage, no explosion. Renders a convincing flash-bang burst (bright white dust,
	 * explosion emitter, sparks, smoke) and plays a high-pitched bang sound, then applies the configured potion effects
	 * to every living entity within {@code explosionRadius}, including the thrower.
	 */
	private void detonateStun(Location center, ThrowableData data, World world) {
		double radius = data.getExplosionRadius();
		ParticleUtil.spawnFlashbangBurst(center, radius);
		world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.8f);

		List<PotionEffect> effects = PotionEffectParser.parseList(data.getEffects());
		if (effects.isEmpty()) return;

		double radiusSq = radius * radius;

		for (Entity nearby : world.getNearbyEntities(center, radius, radius, radius)) {
			if (!(nearby instanceof LivingEntity target)) continue;
			if (target.getLocation().distanceSquared(center) > radiusSq) continue;
			for (PotionEffect effect : effects) target.addPotionEffect(effect);
		}
	}

	/**
	 * Smoke cloud detonation: no damage. Spawns a {@link RepeatingTimer} that emits smoke particles in a sphere of
	 * {@code cloudRadius} (or {@code explosionRadius} when zero) and re-applies the configured potion effects to all
	 * living entities inside the cloud every 10 ticks. The timer self-cancels after {@code cloudDuration} ticks.
	 */
	private void spawnSmokeCloud(Location center, ThrowableData data, World world) {
		List<PotionEffect> effects = PotionEffectParser.parseList(data.getEffects());

		double radius   = data.getCloudRadius() > 0 ? data.getCloudRadius() : data.getExplosionRadius();
		double radiusSq = radius * radius;
		int    duration = data.getCloudDuration();

		// initial detonation burst — sudden outward smoke expansion
		ParticleUtil.spawnSmokeCloudBurst(center, radius);

		int[] elapsed = {0};
		RepeatingTimer cloud = new RepeatingTimer(plugin, 1L, time -> {
			if (elapsed[0] >= duration) {
				time.stop();
				return;
			}
			elapsed[0]++;

			// dense smoke cloud that fades naturally toward expiration
			double intensity = 1.0 - ((double) elapsed[0] / duration);
			ParticleUtil.spawnDenseSmokeCloud(center, radius, intensity);

			// refresh effects on entities inside the cloud every 10 ticks
			if (elapsed[0] % 10 != 0 || effects.isEmpty()) return;
			for (Entity nearby : world.getNearbyEntities(center, radius, radius, radius)) {
				if (!(nearby instanceof LivingEntity target)) continue;
				if (target.getLocation().distanceSquared(center) > radiusSq) continue;
				for (PotionEffect effect : effects) target.addPotionEffect(effect);
			}
		});

		cloud.start(false);
	}

}
