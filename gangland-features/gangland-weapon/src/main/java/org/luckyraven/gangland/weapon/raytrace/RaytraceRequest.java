package org.luckyraven.gangland.weapon.raytrace;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.events.projectile.WeaponRaytraceImpactEvent;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Immutable description of a single weapon raytrace. Built by a weapon action and submitted to
 * {@link WeaponRaytracer#fireInstant} (for hitscan) or driven through {@link WeaponRaytracer#advanceSegment} per tick
 * (for stepped slow projectiles).
 * <p>
 * For weapons that want the standard damage pipeline (rifles, melee), leave {@link #impactHandler} null. For weapons
 * with non-standard semantics (incendiary fire ticks, biological potion effects), supply a custom handler — it is
 * invoked on every uncancelled impact instead of the default damage application.
 */
@Getter
public class RaytraceRequest {

	private final LivingEntity                        shooter;
	private final Weapon                              weapon;
	private final Location                            origin;
	private final Vector                              direction;
	private final double                              maxDistance;
	private final double                              baseDamage;
	private final double                              hitboxExpansion;
	private final int                                 maxIterations;
	private final Predicate<Entity>                   entityFilter;
	private final Consumer<WeaponRaytraceImpactEvent> impactHandler;
	private final double                              gravity;
	private final double                              projectileSpeed;

	private RaytraceRequest(Builder b) {
		this.shooter         = b.shooter;
		this.weapon          = b.weapon;
		this.origin          = b.origin;
		this.direction       = b.direction;
		this.maxDistance     = b.maxDistance;
		this.baseDamage      = b.baseDamage;
		this.hitboxExpansion = b.hitboxExpansion;
		this.maxIterations   = b.maxIterations;
		this.entityFilter    = b.entityFilter;
		this.impactHandler   = b.impactHandler;
		this.gravity         = b.gravity;
		this.projectileSpeed = b.projectileSpeed;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private LivingEntity                        shooter;
		private Weapon                              weapon;
		private Location                            origin;
		private Vector                              direction;
		private double                              maxDistance     = 64.0;
		private double                              baseDamage;
		private double                              hitboxExpansion = 0.3;
		private int                                 maxIterations   = 8;
		private Predicate<Entity>                   entityFilter    = e -> true;
		private Consumer<WeaponRaytraceImpactEvent> impactHandler;
		private double                              gravity         = 0.0;
		private double                              projectileSpeed = 1.0;

		public Builder shooter(LivingEntity shooter) {
			this.shooter = shooter;
			return this;
		}

		public Builder weapon(Weapon weapon) {
			this.weapon = weapon;
			return this;
		}

		public Builder origin(Location origin) {
			this.origin = origin;
			return this;
		}

		public Builder direction(Vector direction) {
			this.direction = direction;
			return this;
		}

		public Builder maxDistance(double maxDistance) {
			this.maxDistance = maxDistance;
			return this;
		}

		public Builder baseDamage(double baseDamage) {
			this.baseDamage = baseDamage;
			return this;
		}

		public Builder hitboxExpansion(double hitboxExpansion) {
			this.hitboxExpansion = hitboxExpansion;
			return this;
		}

		public Builder maxIterations(int maxIterations) {
			this.maxIterations = maxIterations;
			return this;
		}

		public Builder entityFilter(Predicate<Entity> entityFilter) {
			this.entityFilter = entityFilter;
			return this;
		}

		public Builder impactHandler(@Nullable Consumer<WeaponRaytraceImpactEvent> impactHandler) {
			this.impactHandler = impactHandler;
			return this;
		}

		public Builder gravity(double gravity) {
			this.gravity = gravity;
			return this;
		}

		public Builder projectileSpeed(double projectileSpeed) {
			this.projectileSpeed = projectileSpeed;
			return this;
		}

		public RaytraceRequest build() {
			if (shooter == null || weapon == null || origin == null || direction == null) {
				throw new IllegalStateException("RaytraceRequest requires shooter, weapon, origin, and direction");
			}
			return new RaytraceRequest(this);
		}

	}

}
