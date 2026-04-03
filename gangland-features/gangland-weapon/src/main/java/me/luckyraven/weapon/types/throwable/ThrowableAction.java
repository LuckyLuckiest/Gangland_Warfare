package me.luckyraven.weapon.types.throwable;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.utilities.ParticleUtil;
import me.luckyraven.weapon.dto.ThrowableData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
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

		decrementHeldStack(player);

		World    world  = player.getWorld();
		Location eyeLoc = player.getEyeLocation();

		Item grenade = world.dropItem(eyeLoc, new ItemStack(weapon.getMaterial()));
		grenade.setPickupDelay(Integer.MAX_VALUE);

		Vector throwVec = eyeLoc.getDirection().normalize().multiply(1.2).add(new Vector(0, 0.2, 0));
		grenade.setVelocity(throwVec);

		SoundConfiguration.playSounds(player, weapon.getSoundData().getShotCustom(),
		                              weapon.getSoundData().getShotDefault());
		weapon.getRecoil().applyRecoil(recoilCompatibility, player);

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
			boolean hitSurface = tickCount[0] > 1 && !onGround && curLenSq < 0.005 && prevVelocityLenSq[0] > 0.01
			                     && bounceCooldown[0] == 0;
			// For sticky grenades: also catch angled wall hits where the reflected velocity is
			// reduced but not near-zero. If velocity magnitude squared drops to < 40 % of the
			// previous tick's value, a collision absorbed kinetic energy → stick immediately.
			boolean stickyCollision = data.isSticky() && tickCount[0] > 2 && bounceCooldown[0] == 0
			                          && prevVelocityLenSq[0] > 0.05 && curLenSq < prevVelocityLenSq[0] * 0.40;
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
		                      data.getFireTicks() > 0, false, player);

		// Clean up entries not consumed by CarDamageListener (e.g. entity out of actual blast range)
		plugin.getServer()
		      .getScheduler()
		      .runTaskLater(plugin, () -> registeredUuids.forEach(pendingVehicleExplosionDamage::remove), 1L);

		for (Entity nearby : world.getNearbyEntities(center, data.getExplosionRadius(), data.getExplosionRadius(),
		                                             data.getExplosionRadius())) {
			if (!(nearby instanceof LivingEntity target)) continue;
			if (nearby.getLocation().distanceSquared(center) > radiusSq) continue;
			if (totalDmg > 0) {
				UUID targetUuid = target.getUniqueId();
				pendingDamage.add(targetUuid);
				pendingKillerWeapon.put(targetUuid, weapon.getName());
				target.damage(totalDmg, player);
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

}
