package me.luckyraven.weapon.modifiers;

import com.cryptomorin.xseries.XAttribute;
import com.cryptomorin.xseries.particles.XParticle;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.dto.ModifiersData;
import me.luckyraven.weapon.projectile.ProjectileState;
import me.luckyraven.weapon.projectile.WeaponProjectile;
import me.luckyraven.weapon.types.gun.GunWeapon;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * Handles the application and effects of weapon modifiers during projectile interactions.
 */
public class ModifierHandler {

	/**
	 * Calculates final damage with armor piercing modifier applied.
	 *
	 * @param baseDamage The base damage amount
	 * @param target The target entity
	 * @param weapon The weapon used
	 *
	 * @return The final damage after armor calculations
	 */
	public static double calculateArmorPiercingDamage(double baseDamage, LivingEntity target, Weapon weapon) {
		ModifiersData modifiers = weapon.getModifiersData();

		if (!modifiers.hasArmorPiercing()) {
			return baseDamage;
		}

		ArmorPiercingModifier armorPiercing = modifiers.getArmorPiercing();

		// Get target's armor value using XAttribute for cross-version compatibility
		Attribute armorAttribute = XAttribute.ARMOR.get();
		if (armorAttribute == null) {
			return baseDamage;
		}

		AttributeInstance armorInstance = target.getAttribute(armorAttribute);
		if (armorInstance == null) {
			return baseDamage;
		}

		double armor          = armorInstance.getValue();
		double effectiveArmor = armorPiercing.calculateEffectiveArmor(armor);

		// Minecraft damage reduction formula: damage * (1 - min(20, armor) / 25)
		double normalReduction   = Math.min(20, armor) / 25.0;
		double piercingReduction = Math.min(20, effectiveArmor) / 25.0;

		// Calculate the damage difference
		double normalDamage   = baseDamage * (1 - normalReduction);
		double piercingDamage = baseDamage * (1 - piercingReduction);

		// Return the piercing damage (will be reduced again by Minecraft, so we compensate)
		return baseDamage + (piercingDamage - normalDamage);
	}

	/**
	 * Adds the flat damage bonus to the base damage.
	 *
	 * @param baseDamage The damage before the bonus
	 * @param weapon The weapon used
	 *
	 * @return The damage with the flat bonus added, or baseDamage unchanged if modifier is absent
	 */
	public static double applyFlatDamage(double baseDamage, Weapon weapon) {
		ModifiersData modifiers = weapon.getModifiersData();

		if (!modifiers.hasFlatDamage()) {
			return baseDamage;
		}

		return baseDamage + modifiers.getFlatDamage().bonus();
	}

	/**
	 * Handles entity penetration logic.
	 *
	 * @param state The projectile state
	 *
	 * @return true if the projectile should continue, false if it should stop
	 */
	public static boolean handleEntityPenetration(ProjectileState state) {
		if (!state.canPenetrateEntity()) {
			return false;
		}

		PenetrationModifier penetration = state.getWeapon().getModifiersData().getPenetration();

		// Increment penetration count
		state.setEntitiesPenetrated(state.getEntitiesPenetrated() + 1);

		// Apply damage reduction
		state.applyPenetrationReduction(penetration.damageReduction());

		// Check if projectile can continue
		return state.canPenetrateEntity() || state.canPenetrateBlock();
	}

	/**
	 * Handles block penetration logic.
	 *
	 * @param state The projectile state
	 * @param hitBlock The block that was hit
	 *
	 * @return true if the projectile should continue through the block
	 */
	public static boolean handleBlockPenetration(ProjectileState state, Block hitBlock) {
		if (!state.canPenetrateBlock()) {
			return false;
		}

		PenetrationModifier penetration = state.getWeapon().getModifiersData().getPenetration();

		// Check if block is penetrable (non-solid or thin blocks)
		if (!isPenetrableBlock(hitBlock.getType())) {
			return false;
		}

		// Increment penetration count
		state.setBlocksPenetrated(state.getBlocksPenetrated() + 1);

		// Apply damage reduction
		state.applyPenetrationReduction(penetration.damageReduction());

		return true;
	}

	/**
	 * Handles ricochet logic when a projectile hits a block.
	 * <p>
	 * Spigot removes the original projectile the moment it hits a block, so reusing it for the bounce never produces a
	 * visible deflected trajectory. Instead, this method instantiates a fresh {@link WeaponProjectile} via
	 * {@code ProjectileType#createInstance} (the same path used by {@code GunAction}) and relaunches it through
	 * {@link WeaponProjectile#launchProjectile(Location, Vector)} at the impact point with the reflected velocity.
	 *
	 * @param plugin The owning plugin (needed by the new {@code WeaponProjectile}'s scheduled tasks)
	 * @param state The projectile state
	 * @param projectile The projectile entity
	 * @param hitBlock The block that was hit
	 * @param hitBlockFace The face of the block that was hit
	 *
	 * @return the newly spawned ricochet projectile, or {@code null} if the projectile did not ricochet
	 */
	public static Projectile handleRicochet(JavaPlugin plugin, ProjectileState state, Projectile projectile,
	                                        Block hitBlock, BlockFace hitBlockFace) {
		if (!state.canRicochet()) {
			return null;
		}

		ModifiersData modifiers     = state.getWeapon().getModifiersData();
		Material      blockMaterial = hitBlock.getType();

		// Find a matching ricochet modifier
		RicochetModifier matchingModifier = null;
		for (RicochetModifier modifier : modifiers.getRicochets()) {
			if (modifier.canBounceOff(blockMaterial) && state.getBounceCount() < modifier.maxBounces()) {
				matchingModifier = modifier;
				break;
			}
		}

		if (matchingModifier == null) {
			return null;
		}

		if (!(projectile.getShooter() instanceof LivingEntity shooter)) {
			return null;
		}

		GunWeapon gunWeapon = (GunWeapon) state.getWeapon();

		// Calculate reflected velocity
		Vector velocity  = projectile.getVelocity();
		Vector normal    = getBlockFaceNormal(hitBlockFace);
		Vector reflected = reflectVector(velocity, normal);

		// Instantiate a brand-new WeaponProjectile of the same type as the original (mirrors GunAction's flow)
		// and relaunch it at the impact location with the reflected velocity. The original is removed below.
		WeaponProjectile<?> ricochetProjectile = gunWeapon.getProjectileData()
		                                                  .getType()
		                                                  .createInstance(plugin, shooter, gunWeapon);

		// Offset slightly along the surface normal so the new projectile does not immediately re-collide.
		Location spawnLoc = projectile.getLocation().clone().add(normal.clone().multiply(0.5));

		Projectile newProjectile = ricochetProjectile.launchProjectile(spawnLoc, reflected);

		// If the underlying spawn could not happen (e.g. impact location had no world), bail out without destroying
		// the original — let the normal block-hit cleanup path run instead.
		if (newProjectile == null) {
			return null;
		}

		// Remove the spent original projectile
		projectile.remove();

		// Increment bounce count and apply damage reduction (carried over to the new projectile via state migration
		// in ProjectileDamageListener)
		state.setBounceCount(state.getBounceCount() + 1);
		state.applyRicochetReduction(matchingModifier.damageRetention());

		// Play ricochet effect
		playRicochetEffect(hitBlock.getLocation().add(0.5, 0.5, 0.5));

		return newProjectile;
	}

	/**
	 * Spawns tracer particles along the projectile path.
	 *
	 * @param weapon The weapon with tracer modifier
	 * @param from Start location
	 * @param to End location
	 */
	public static void spawnTracerParticles(Weapon weapon, Location from, Location to) {
		ModifiersData modifiers = weapon.getModifiersData();

		if (!modifiers.hasTracer()) {
			return;
		}

		TracerModifier tracer = modifiers.getTracer();
		World          world  = from.getWorld();

		if (world == null || !world.equals(to.getWorld())) {
			return;
		}

		double distance = from.distance(to);
		int    points   = (int) Math.ceil(distance * 2); // 2 particles per block

		double deltaX = (to.getX() - from.getX()) / points;
		double deltaY = (to.getY() - from.getY()) / points;
		double deltaZ = (to.getZ() - from.getZ()) / points;

		// Use XParticle for cross-version compatibility
		Particle dustParticle = XParticle.DUST.get();
		Particle.DustOptions dustOptions = new Particle.DustOptions(
				org.bukkit.Color.fromRGB(tracer.color().getRed(), tracer.color().getGreen(), tracer.color().getBlue()),
				tracer.particleSize());

		for (int i = 0; i <= points; i++) {
			double x = from.getX() + (deltaX * i);
			double y = from.getY() + (deltaY * i);
			double z = from.getZ() + (deltaZ * i);

			Location particleLocation = new Location(world, x, y, z);
			world.spawnParticle(Objects.requireNonNull(dustParticle), particleLocation, 1, dustOptions);
		}
	}

	/**
	 * Checks if a block can be penetrated by projectiles.
	 */
	private static boolean isPenetrableBlock(Material material) {
		String name = material.name();

		// Glass and thin blocks
		if (name.contains("GLASS") || name.contains("PANE")) return true;
		if (name.contains("LEAVES")) return true;
		if (name.contains("FENCE") && !name.contains("GATE")) return true;
		if (name.contains("BARS")) return true;
		if (name.contains("CHAIN")) return true;
		if (name.contains("CARPET")) return true;
		if (name.contains("BANNER")) return true;
		if (name.contains("SIGN")) return true;
		if (name.contains("CANDLE")) return true;
		if (name.contains("FLOWER") || name.contains("PLANT") || name.contains("GRASS")) return true;
		if (name.contains("VINE") || name.contains("MOSS")) return true;

		return switch (material) {
			case COBWEB, SNOW, SUGAR_CANE, BAMBOO, SCAFFOLDING, LADDER -> true;
			default -> false;
		};
	}

	/**
	 * Gets the normal vector for a block face.
	 */
	private static Vector getBlockFaceNormal(BlockFace face) {
		return switch (face) {
			case DOWN -> new Vector(0, -1, 0);
			case NORTH -> new Vector(0, 0, -1);
			case SOUTH -> new Vector(0, 0, 1);
			case EAST -> new Vector(1, 0, 0);
			case WEST -> new Vector(-1, 0, 0);
			default -> new Vector(0, 1, 0);
		};
	}

	/**
	 * Reflects a vector off a surface with a given normal.
	 */
	private static Vector reflectVector(Vector velocity, Vector normal) {
		// Reflection formula: R = V - 2(V·N)N
		double dot = velocity.dot(normal);
		return velocity.clone().subtract(normal.clone().multiply(2 * dot));
	}

	/**
	 * Plays a ricochet sound and particle effect.
	 */
	private static void playRicochetEffect(Location location) {
		World world = location.getWorld();
		if (world == null) return;

		// Spark particles using XParticle for cross-version compatibility
		Particle critParticle = XParticle.CRIT.get();
		world.spawnParticle(Objects.requireNonNull(critParticle), location, 5, 0.1, 0.1, 0.1, 0.05);

		// Ricochet sound using XSound for cross-version compatibility
		SoundConfiguration sound = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
		                                                  Sound.BLOCK_ANVIL_LAND.toString(), 0.3f, 2.0f);
		SoundConfiguration.playSoundsAtLocation(location, sound, null);
	}

}
