package org.luckyraven.gangland.weapon.raytrace;

import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.luckyraven.keystone.timer.RepeatingTimer;

import java.util.List;

/**
 * Per-tick driver for slow visual projectiles (rockets, flares, throwables). Wraps a cosmetic Bukkit projectile entity
 * that handles motion via Spigot's normal physics, and calls {@link WeaponRaytracer#advanceSegment} once per tick to
 * perform hit detection along the segment the visual moved through.
 * <p>
 * Decouples hit detection from Spigot's projectile entity lifecycle: even if Spigot kills the visual on first block
 * contact, this task catches the impact during the segment scan that preceded the kill, so penetration and ricochet
 * still work consistently.
 * <p>
 * On terminal impact (the ray runs out of distance, iterations, or hits a non-penetrable target), the task removes the
 * visual and — if {@code explodeOnTerminate} is set — fires an AOE explosion at the impact point with linear damage
 * falloff. Mirrors the legacy {@code ProjectileDamageListener#explosiveProjectile} behaviour for backwards
 * compatibility with rocket weapons.
 */
public class SteppedProjectileTask {

	private final JavaPlugin          plugin;
	private final WeaponRaytracer     raytracer;
	private final WeaponVisualSpawner visualSpawner;
	private final Projectile          visual;
	private final RaytraceContext     ctx;
	private final boolean             explodeOnTerminate;
	private final double              explosionRadius;
	private final int                 maxTicks;

	private Location       lastLoc;
	private RepeatingTimer timer;
	private int            tickCounter;
	private boolean        finished;

	public SteppedProjectileTask(JavaPlugin plugin, WeaponRaytracer raytracer, WeaponVisualSpawner visualSpawner,
	                             Projectile visual, RaytraceContext ctx, boolean explodeOnTerminate,
	                             double explosionRadius, int maxTicks) {
		this.plugin             = plugin;
		this.raytracer          = raytracer;
		this.visualSpawner      = visualSpawner;
		this.visual             = visual;
		this.ctx                = ctx;
		this.explodeOnTerminate = explodeOnTerminate;
		this.explosionRadius    = explosionRadius;
		this.maxTicks           = maxTicks;
		this.lastLoc            = visual.getLocation();
		this.tickCounter        = 0;
		this.finished           = false;
	}

	public void start() {
		timer = new RepeatingTimer(plugin, 1L, task -> {
			if (finished) {
				task.stop();
				return;
			}

			if (++tickCounter > maxTicks) {
				terminate(visual.getLocation());
				task.stop();
				return;
			}

			// Spigot may have killed the visual on a block contact this tick — fall back to the
			// last known position so the explosion still happens at the right spot.
			if (visual.isDead() || !visual.isValid()) {
				terminate(lastLoc);
				task.stop();
				return;
			}

			Location currentLoc = visual.getLocation();
			Vector   segment    = currentLoc.toVector().subtract(lastLoc.toVector());

			// No movement this tick (e.g. fireball stuck on first frame) — wait for next tick.
			if (segment.lengthSquared() < 1e-6) {
				return;
			}

			raytracer.advanceSegment(ctx, lastLoc, currentLoc);

			// Ray exhausted: either the loop's distance budget is gone, the iteration cap was hit,
			// or a non-penetrable target stopped it. Pick the last tracer point as the impact site
			// and fire the AOE.
			if (ctx.getRemaining() <= 0) {
				Location impactPoint = lastTracerPoint();
				if (impactPoint == null) {
					impactPoint = currentLoc;
				}
				terminate(impactPoint);
				task.stop();
				return;
			}

			lastLoc = currentLoc;
		});
		timer.start(false);
	}

	private Location lastTracerPoint() {
		List<Location> segments = ctx.getTracerSegments();
		if (segments.isEmpty()) {
			return null;
		}
		return segments.get(segments.size() - 1);
	}

	private void terminate(Location impactLocation) {
		if (finished) {
			return;
		}
		finished = true;

		visualSpawner.unregisterCosmetic(visual.getEntityId());
		if (!visual.isDead() && visual.isValid()) {
			visual.remove();
		}

		if (explodeOnTerminate && impactLocation != null) {
			fireExplosion(impactLocation);
		}
	}

	/**
	 * AOE damage with linear falloff plus visual/sound effects. Mirrors the legacy explosion behaviour from
	 * {@code ProjectileDamageListener#explosiveProjectile}.
	 */
	private void fireExplosion(Location loc) {
		World world = loc.getWorld();
		if (world == null) {
			return;
		}

		LivingEntity shooter = ctx.getRequest().getShooter();
		for (Entity entity : world.getNearbyEntities(loc, explosionRadius, explosionRadius, explosionRadius)) {
			if (!(entity instanceof LivingEntity target)) continue;
			if (target.equals(shooter)) continue;

			double distance = target.getLocation().distance(loc);
			double damage   = 20 * (1 - (distance / explosionRadius));
			if (damage > 0) {
				target.damage(damage, shooter);
			}
		}

		world.spawnParticle(XParticle.EXPLOSION.get(), loc, 5, 0.5, 0.5, 0.5, 0.1);
		world.spawnParticle(XParticle.SMOKE.get(), loc, 30, 1.0, 1.0, 1.0, 0.1);
		world.spawnParticle(XParticle.FLAME.get(), loc, 20, 1.0, 1.0, 1.0, 0.1);
		world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
	}

}
