package org.luckyraven.gangland.weapon.raytrace;

import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.configuration.SoundConfiguration;
import org.luckyraven.gangland.core.utilities.ParticleUtil;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.dto.DamageData;
import org.luckyraven.gangland.weapon.events.projectile.WeaponRaytraceImpactEvent;
import org.luckyraven.gangland.weapon.modifiers.BlockDamageManager;
import org.luckyraven.gangland.weapon.modifiers.ModifierHandler;
import org.luckyraven.gangland.weapon.modifiers.action.BlockBreakModifier;
import org.luckyraven.gangland.weapon.modifiers.action.RicochetModifier;
import org.luckyraven.gangland.weapon.modifiers.action.TracerModifier;
import org.luckyraven.gangland.weapon.projectile.ProjectileState;
import org.luckyraven.gangland.weapon.types.gun.GunWeapon;
import org.luckyraven.gangland.weapon.wearable.WearableService;

import java.util.List;
import java.util.Random;

/**
 * Unified server-side raytracer used by every weapon action (gun, incendiary, biological, melee, throwable). Decouples
 * hit detection from the Bukkit projectile entity lifecycle so penetration, ricochet, and block-break modifiers all
 * work even when Spigot destroys the visual projectile on the first contact.
 * <p>
 * Two entry points share the same {@link #advanceRay} loop:
 * <ul>
 *   <li>{@link #fireInstant(RaytraceRequest)} — runs the loop synchronously to completion in one
 *       tick. Used for hitscan shots (rifles, shotgun pellets, fire spray, biological clouds,
 *       melee swings).</li>
 *   <li>{@link #advanceSegment(RaytraceContext, Location, Location)} — runs zero or more iterations
 *       covering exactly one segment travelled by a slow visual projectile in a single tick. Used
 *       by {@code SteppedProjectileTask} for rockets and throwables.</li>
 * </ul>
 * <p>
 * Each impact fires a {@link WeaponRaytraceImpactEvent}. Listeners (cops-n-crooks NPC AI, vehicle
 * damage handlers) react uniformly regardless of which weapon action originated the shot. If a
 * weapon needs non-standard impact behaviour (incendiary fire ticks, biological potion effects),
 * it supplies a custom {@code impactHandler} on its {@link RaytraceRequest}.
 */
public class WeaponRaytracer {

	/**
	 * Length of each straight-line segment used to approximate the parabolic drop arc beyond the effective range.
	 * Smaller values produce a smoother curve but require more raytrace calls per shot. 3 blocks gives sub-pixel arc
	 * error even at high gravity values while keeping raytrace call count low (20 calls for a 60-block drop phase).
	 */
	private static final double GRAVITY_STEP   = 3.0;
	/**
	 * Maximum distance (blocks) a bullet can travel in the gravity drop phase beyond its configured effective range.
	 * Acts as a safety cap to prevent runaway raytrace loops for bullets that never hit geometry (e.g. fired straight
	 * up).
	 */
	private static final double MAX_DROP_RANGE = 200.0;

	/**
	 * Set while {@link #handleEntityImpact} is inside a {@code LivingEntity#damage} call. Listeners that handle
	 * {@code EntityDamageByEntityEvent} for weapon-system reactions (cops-n-crooks NPC AI) check this flag and skip —
	 * they receive the canonical signal via {@link WeaponRaytraceImpactEvent} instead, so they would otherwise
	 * double-process the same shot. Use {@link #isRaytraceDamageInProgress()} from listener code.
	 */
	private static final ThreadLocal<Boolean> RAYTRACE_DAMAGE_IN_PROGRESS =
			ThreadLocal.withInitial(() -> Boolean.FALSE);
	private final        WeaponService        weaponService;
	private final        WearableService      wearableService;
	private final        BlockDamageManager   blockDamageManager;
	private final        WeaponVisualSpawner  visualSpawner;
	private final        Random               random;

	public WeaponRaytracer(WeaponService weaponService, WearableService wearableService,
	                       BlockDamageManager blockDamageManager, WeaponVisualSpawner visualSpawner) {
		this.weaponService      = weaponService;
		this.wearableService    = wearableService;
		this.blockDamageManager = blockDamageManager;
		this.visualSpawner      = visualSpawner;
		this.random             = new Random();
	}

	public static boolean isRaytraceDamageInProgress() {
		return RAYTRACE_DAMAGE_IN_PROGRESS.get();
	}

	private static Vector blockFaceNormal(BlockFace face) {
		return switch (face) {
			case DOWN -> new Vector(0, -1, 0);
			case NORTH -> new Vector(0, 0, -1);
			case SOUTH -> new Vector(0, 0, 1);
			case EAST -> new Vector(1, 0, 0);
			case WEST -> new Vector(-1, 0, 0);
			default -> new Vector(0, 1, 0);
		};
	}

	// ------------------------------------------------------------------
	// Public entry points
	// ------------------------------------------------------------------

	private static Vector reflect(Vector velocity, Vector normal) {
		double dot = velocity.dot(normal);
		return velocity.clone().subtract(normal.clone().multiply(2 * dot));
	}

	/**
	 * Computes the point at which a ray exits the given axis-aligned bounding box, given an entry point on (or inside)
	 * the box and a direction. Used after a penetration to advance the raytrace origin to <em>just past</em> the back
	 * face of the penetrated block or entity, so the next iteration of the loop doesn't immediately re-detect the same
	 * target.
	 * <p>
	 * Implements the standard slab method: for each axis, compute the parameter {@code t} at which the ray crosses the
	 * far face of the box; the smallest positive {@code t} gives the exit point. A small epsilon is added so the
	 * returned location is strictly past the back face.
	 */
	private static Location advancePastBox(BoundingBox box, Location entry, Vector dir) {
		Vector origin = entry.toVector();

		double tMaxX = Double.POSITIVE_INFINITY;
		double tMaxY = Double.POSITIVE_INFINITY;
		double tMaxZ = Double.POSITIVE_INFINITY;

		if (dir.getX() > 1e-9) {
			tMaxX = (box.getMaxX() - origin.getX()) / dir.getX();
		} else if (dir.getX() < -1e-9) {
			tMaxX = (box.getMinX() - origin.getX()) / dir.getX();
		}

		if (dir.getY() > 1e-9) {
			tMaxY = (box.getMaxY() - origin.getY()) / dir.getY();
		} else if (dir.getY() < -1e-9) {
			tMaxY = (box.getMinY() - origin.getY()) / dir.getY();
		}

		if (dir.getZ() > 1e-9) {
			tMaxZ = (box.getMaxZ() - origin.getZ()) / dir.getZ();
		} else if (dir.getZ() < -1e-9) {
			tMaxZ = (box.getMinZ() - origin.getZ()) / dir.getZ();
		}

		double exitT = Math.min(Math.min(tMaxX, tMaxY), tMaxZ);
		if (exitT == Double.POSITIVE_INFINITY || exitT < 0) {
			exitT = 0;
		}

		// Tiny epsilon ensures the new origin is strictly past the back face.
		return entry.clone().add(dir.clone().multiply(exitT + 0.01));
	}

	// ------------------------------------------------------------------
	// Inner loop
	// ------------------------------------------------------------------

	public WeaponVisualSpawner getVisualSpawner() {
		return visualSpawner;
	}

	// ------------------------------------------------------------------
	// Impact handling
	// ------------------------------------------------------------------

	/**
	 * Runs the full hitscan loop for a single ray, synchronously, until the ray stops (no more penetration, no more
	 * ricochet, no more distance). The supplied request must carry an origin already at the muzzle position — see
	 * {@link WeaponMuzzle#compute}.
	 */
	public void fireInstant(RaytraceRequest request) {
		ProjectileState state = new ProjectileState(request.getWeapon(), request.getBaseDamage());
		RaytraceContext ctx   = new RaytraceContext(request, state);

		while (advanceRay(ctx, ctx.getRemaining())) {
			// loop until advanceRay reports stop
		}

		// If the ray exhausted its effective range without a terminal hit and gravity is configured,
		// continue the bullet beyond the effective range with a parabolic drop until it lands.
		if (request.getGravity() > 0 && ctx.getRemaining() <= 0) {
			extendWithGravity(ctx);
		}

		flushTracer(ctx);
	}

	/**
	 * Runs the loop over exactly one segment of travel — from {@code from} to {@code to}. Used by stepped slow
	 * projectiles whose visual entity moved a small amount during the current tick. The context must persist across
	 * calls so penetration / ricochet counters carry over.
	 */
	public void advanceSegment(RaytraceContext ctx, Location from, Location to) {
		Vector segment = to.toVector().subtract(from.toVector());
		if (segment.lengthSquared() < 1e-6) {
			return;
		}

		ctx.setCurrentOrigin(from.clone());
		ctx.setCurrentDir(segment.clone().normalize());
		ctx.setRemaining(segment.length() + 0.01);

		while (advanceRay(ctx, ctx.getRemaining())) {
			// loop until advanceRay reports stop
		}
	}

	/**
	 * Continues a bullet beyond its effective range with a parabolic gravity arc. Called when the straight-line hitscan
	 * phase exhausted its distance budget without a terminal hit. The bullet maintains its horizontal direction but
	 * curves downward according to the kinematic equation:
	 * <pre>
	 *   y(s) = startY + dirY * s − 0.5 * gravity * (s / speed)²
	 * </pre>
	 * where {@code s} is the distance traveled beyond the effective range and {@code speed} is the projectile speed
	 * from the weapon config. Faster bullets drop less because gravity has less "time" to act. The extension continues
	 * until the ray hits a block or entity, or the {@link #MAX_DROP_RANGE} safety cap is reached.
	 */
	private void extendWithGravity(RaytraceContext ctx) {
		RaytraceRequest request = ctx.getRequest();
		double          gravity = request.getGravity();
		double          speed   = Math.max(0.1, request.getProjectileSpeed());
		Vector          dir     = ctx.getCurrentDir().clone().normalize();

		// Start from the last known position — the endpoint of the straight-line phase.
		List<Location> segments = ctx.getTracerSegments();
		Location startPos = segments.isEmpty()
		                    ? ctx.getCurrentOrigin().clone()
		                    : segments.get(segments.size() - 1).clone();

		double   traveled = 0;
		Location current  = startPos.clone();

		while (traveled < MAX_DROP_RANGE) {
			double step         = Math.min(GRAVITY_STEP, MAX_DROP_RANGE - traveled);
			double nextTraveled = traveled + step;

			// Parabolic arc: horizontal displacement continues at the same rate while
			// vertical position drops quadratically with distance.
			double tNext = nextTraveled / speed;
			Location next = startPos.clone().add(
					dir.getX() * nextTraveled,
					dir.getY() * nextTraveled - 0.5 * gravity * tNext * tNext,
					dir.getZ() * nextTraveled
			);

			// Reset iteration counter so each segment gets a full budget — without this,
			// iterations accumulate across segments and hit the cap prematurely.
			ctx.setIterations(0);
			advanceSegment(ctx, current, next);

			// advanceSegment sets remaining = segment.length + 0.01, then advanceRay
			// zeros it on miss but leaves it positive on a terminal hit.
			if (ctx.getRemaining() > 0.02) {
				break;
			}

			current  = next;
			traveled = nextTraveled;
		}
	}

	/**
	 * Performs a single iteration of the raytrace loop. Returns {@code true} if the caller should call again (the ray
	 * continued past a penetration or ricochet) and {@code false} if the ray has terminated.
	 */
	private boolean advanceRay(RaytraceContext ctx, double segmentLimit) {
		RaytraceRequest request = ctx.getRequest();

		if (ctx.getIterations() >= request.getMaxIterations()) {
			return false;
		}
		ctx.setIterations(ctx.getIterations() + 1);

		if (ctx.getRemaining() <= 0) {
			return false;
		}

		World world = ctx.getCurrentOrigin().getWorld();
		if (world == null) {
			return false;
		}

		double scanDist = Math.min(ctx.getRemaining(), segmentLimit);

		LivingEntity shooter = request.getShooter();
		RayTraceResult blockHit = world.rayTraceBlocks(
				ctx.getCurrentOrigin(), ctx.getCurrentDir(), scanDist,
				FluidCollisionMode.NEVER, true);
		RayTraceResult entityHit = world.rayTraceEntities(
				ctx.getCurrentOrigin(), ctx.getCurrentDir(), scanDist,
				request.getHitboxExpansion(),
				e -> e != shooter
				     && !(e instanceof ItemFrame || e instanceof ArmorStand)
				     && request.getEntityFilter().test(e));

		// Discard hits on entities that are behind or exactly beside the ray origin — these are
		// caught only because hitboxExpansion inflates the detection sphere at the start point.
		if (entityHit != null) {
			Entity candidate = entityHit.getHitEntity();
			if (candidate != null) {
				Vector toCandidate = candidate.getLocation().toVector()
				                              .subtract(ctx.getCurrentOrigin().toVector());
				if (toCandidate.dot(ctx.getCurrentDir()) <= 0) {
					entityHit = null;
				}
			}
		}

		double blockDist = blockHit != null ?
		                   blockHit.getHitPosition().distance(ctx.getCurrentOrigin().toVector()) :
		                   Double.POSITIVE_INFINITY;
		double entityDist = entityHit != null ?
		                    entityHit.getHitPosition().distance(ctx.getCurrentOrigin().toVector()) :
		                    Double.POSITIVE_INFINITY;

		// Entity hit takes precedence on ties — a body in front of a wall takes the shot.
		if (entityHit != null && entityDist <= blockDist) {
			Entity hit = entityHit.getHitEntity();
			if (hit == null) return false;

			Location impactPt = entityHit.getHitPosition().toLocation(world);

			handleEntityImpact(hit, impactPt, ctx);
			ctx.getTracerSegments().add(impactPt);

			if (ModifierHandler.handleEntityPenetration(ctx.getState())) {
				Location pastEntity  = advancePastBox(hit.getBoundingBox(), impactPt, ctx.getCurrentDir());
				double   advanceDist = pastEntity.toVector().distance(ctx.getCurrentOrigin().toVector());
				ctx.setCurrentOrigin(pastEntity);
				ctx.setRemaining(ctx.getRemaining() - advanceDist);
				return true;
			}
			return false;
		}

		if (blockHit != null) {
			Location impactPt = blockHit.getHitPosition().toLocation(world);
			Block    block    = blockHit.getHitBlock();
			if (block == null) return false;

			BlockFace face = blockHit.getHitBlockFace();
			if (face == null) return false;

			applyBlockBreak(block, request.getWeapon());
			handleBlockImpact(impactPt, block, face, ctx);
			ctx.getTracerSegments().add(impactPt);

			// Penetration before ricochet — matches existing precedence
			if (ModifierHandler.handleBlockPenetration(ctx.getState(), block)) {
				Location pastBlock   = advancePastBox(block.getBoundingBox(), impactPt, ctx.getCurrentDir());
				double   advanceDist = pastBlock.toVector().distance(ctx.getCurrentOrigin().toVector());
				ctx.setCurrentOrigin(pastBlock);
				ctx.setRemaining(ctx.getRemaining() - advanceDist);
				return true;
			}

			RicochetModifier ricochet = matchingRicochet(block.getType(), ctx);
			if (ricochet != null) {
				Vector normal = blockFaceNormal(face);
				ctx.setCurrentDir(reflect(ctx.getCurrentDir(), normal).normalize());
				ctx.getState().setBounceCount(ctx.getState().getBounceCount() + 1);
				ctx.getState().applyRicochetReduction(ricochet.damageRetention());
				ctx.setCurrentOrigin(impactPt.clone().add(normal.clone().multiply(0.05)));
				ctx.setRemaining(ctx.getRemaining() - blockDist);
				return true;
			}

			return false;
		}

		// No hit in this segment — record the end point for tracer rendering and stop.
		ctx.getTracerSegments().add(
				ctx.getCurrentOrigin().clone().add(ctx.getCurrentDir().clone().multiply(scanDist)));
		ctx.setRemaining(0);
		return false;
	}

	// ------------------------------------------------------------------
	// Modifier helpers
	// ------------------------------------------------------------------

	/**
	 * Computes damage, fires {@link WeaponRaytraceImpactEvent}, and (if the event is not cancelled and no custom impact
	 * handler is supplied) applies the standard damage pipeline.
	 */
	private void handleEntityImpact(Entity hit, Location impactPt, RaytraceContext ctx) {
		Weapon       weapon  = ctx.getRequest().getWeapon();
		LivingEntity shooter = ctx.getRequest().getShooter();

		// --- Compute damage (gun-specific extras only when applicable) ---
		boolean criticalHit = false;
		double  damage      = ctx.getState().getCurrentDamage();

		if (hit instanceof LivingEntity living) {
			if (weapon instanceof GunWeapon gun) {
				DamageData dd = gun.getDamageData();
				criticalHit = random.nextDouble() < dd.getCriticalHitChance() / 100D;
				if (criticalHit) {
					damage += wearableService.reduceCritBonus(dd.getCriticalHitDamage(), living);
				}
			}
			damage = ModifierHandler.calculateArmorPiercingDamage(damage, living, weapon);
			damage = wearableService.applyWearableReduction(damage, living, true);
			damage = ModifierHandler.applyFlatDamage(damage, weapon);

			if (weapon instanceof GunWeapon gun
			    && weaponService.isHeadPosition(impactPt, living.getLocation())) {
				damage += gun.getDamageData().getHeadDamage();
			}
		} else {
			// Non-living: skip armor / wearable / headshot. Flat damage still applies so vehicle hits
			// see the configured bonus.
			damage = ModifierHandler.applyFlatDamage(damage, weapon);
		}

		// --- Fire the event ---
		WeaponRaytraceImpactEvent event = new WeaponRaytraceImpactEvent(
				weapon, shooter, hit, null, null, impactPt, damage, ctx.getState());
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			return;
		}

		// --- Custom impact handler short-circuits the default pipeline ---
		if (ctx.getRequest().getImpactHandler() != null) {
			// Set the in-progress flag for the handler too, so any target.damage(...) it makes
			// is recognised as raytracer-driven and the legacy listeners skip it.
			RAYTRACE_DAMAGE_IN_PROGRESS.set(Boolean.TRUE);
			try {
				ctx.getRequest().getImpactHandler().accept(event);
			} finally {
				RAYTRACE_DAMAGE_IN_PROGRESS.set(Boolean.FALSE);
			}
			return;
		}

		// --- Default damage application (LivingEntity only) ---
		if (hit instanceof LivingEntity living) {
			living.setNoDamageTicks(0);
			living.setInvulnerable(false);

			double healthBefore = living.getHealth();

			RAYTRACE_DAMAGE_IN_PROGRESS.set(Boolean.TRUE);
			try {
				living.damage(event.getDamage(), shooter);
			} finally {
				RAYTRACE_DAMAGE_IN_PROGRESS.set(Boolean.FALSE);
			}

			// If health didn't decrease, the damage was blocked (e.g. Citizens spawn protection).
			// Skip all post-damage effects — the hit didn't land.
			boolean damageBlocked = living.isValid() && !living.isDead()
			                        && living.getHealth() >= healthBefore;
			if (damageBlocked) {
				return;
			}

			if (weapon instanceof GunWeapon gun) {
				int fireTicks = wearableService.reduceFireTicks(gun.getDamageData().getFireTicks(), living);
				living.setFireTicks(fireTicks);
			}

			SoundConfiguration impactCustom  = weapon.getSoundData().getImpactCustom();
			SoundConfiguration impactDefault = weapon.getSoundData().getImpactDefault();
			if (impactCustom != null || impactDefault != null) {
				SoundConfiguration.playSoundsAtLocation(living.getLocation(), impactCustom, impactDefault);
			}

			if (criticalHit && shooter instanceof Player player) {
				SoundConfiguration critSound = new SoundConfiguration(
						SoundConfiguration.SoundType.VANILLA, "ITEM_SHIELD_BREAK", 1F, 1F);
				critSound.playSound(player);
			}
		}
	}

	/**
	 * Fires a block-only impact event so listeners can react to "weapon X struck block Y" without an entity being
	 * involved. Unlike entity impacts, this never applies damage by itself — block damage is handled separately by
	 * {@link #applyBlockBreak} via {@link BlockDamageManager}.
	 */
	private void handleBlockImpact(Location impactPt, Block block, BlockFace face, RaytraceContext ctx) {
		WeaponRaytraceImpactEvent event = new WeaponRaytraceImpactEvent(
				ctx.getRequest().getWeapon(),
				ctx.getRequest().getShooter(),
				null, block, face, impactPt, 0.0, ctx.getState());
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			return;
		}

		if (ctx.getRequest().getImpactHandler() != null) {
			ctx.getRequest().getImpactHandler().accept(event);
		}
	}

	private void applyBlockBreak(Block block, Weapon weapon) {
		for (BlockBreakModifier mod : weapon.getModifiersData().getBreakBlocks()) {
			if (!mod.appliesTo(block.getType())) {
				continue;
			}
			blockDamageManager.applyDamage(block, mod);
			break;
		}
	}

	@Nullable
	private RicochetModifier matchingRicochet(Material material, RaytraceContext ctx) {
		if (!ctx.getState().canRicochet()) {
			return null;
		}
		for (RicochetModifier mod : ctx.getRequest().getWeapon().getModifiersData().getRicochets()) {
			if (mod.canBounceOff(material) && ctx.getState().getBounceCount() < mod.maxBounces()) {
				return mod;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------
	// Tracer rendering
	// ------------------------------------------------------------------

	/**
	 * Walks {@code ctx.getTracerSegments()} and draws a particle line for each leg of the raytrace. The path is
	 * {@code [origin, segment[0], segment[1], ...]} so penetration and ricochet legs render as a contiguous trail. Two
	 * overlays may be drawn:
	 * <ul>
	 *   <li>A default gray dust line for any {@link GunWeapon} shot, restoring the legacy
	 *       gun-shoot visualization (point count comes from
	 *       {@code ProjectileData.getDistance()}).</li>
	 *   <li>An opt-in colored line driven by the weapon's {@link TracerModifier}, available to
	 *       any weapon type that configures one.</li>
	 * </ul>
	 */
	private void flushTracer(RaytraceContext ctx) {
		List<Location> segments = ctx.getTracerSegments();
		if (segments.isEmpty()) {
			return;
		}

		Weapon weapon = ctx.getRequest().getWeapon();

		// Default gun-shoot tracer: gray dust line, always rendered for guns so the bullet path is
		// visible even when no TracerModifier is configured.
		if (weapon instanceof GunWeapon gun) {
			Particle.DustOptions defaultOptions = new Particle.DustOptions(Color.GRAY, 0.5F);
			drawTracerLine(ctx, segments, defaultOptions, gun.getProjectileData().getDistance());
		}

		// Configured colored tracer (opt-in via TracerModifier).
		if (weapon.getModifiersData().hasTracer()) {
			TracerModifier tracer = weapon.getModifiersData().getTracer();
			Particle.DustOptions tracerOptions =
					new Particle.DustOptions(tracer.color(), tracer.particleSize());
			drawTracerLine(ctx, segments, tracerOptions, -1);
		}
	}

	/**
	 * Draws a single contiguous dust line through the tracer segments. When {@code fixedPointCount} is positive it is
	 * used directly as the per-leg particle count (matching the legacy "distance from {@code ProjectileData}"
	 * semantics); otherwise the count scales with the leg length.
	 */
	private void drawTracerLine(RaytraceContext ctx, List<Location> segments,
	                            Particle.DustOptions options, int fixedPointCount) {
		Particle dustParticle = XParticle.DUST.get();

		// The first leg starts at the visual muzzle (offset right of the shooter), not at the
		// raytrace origin (which is the eye location). Subsequent legs follow the actual ray.
		Location previous = WeaponMuzzle.compute(ctx.getRequest().getShooter(), ctx.getRequest().getDirection());
		for (Location point : segments) {
			if (previous.getWorld() == null || !previous.getWorld().equals(point.getWorld())) {
				previous = point;
				continue;
			}
			int count = fixedPointCount > 0
			            ? fixedPointCount
			            : Math.max(2, (int) (previous.distance(point) * 4));
			ParticleUtil.spawnLine(previous, point, dustParticle, count, options);
			previous = point;
		}
	}

}
