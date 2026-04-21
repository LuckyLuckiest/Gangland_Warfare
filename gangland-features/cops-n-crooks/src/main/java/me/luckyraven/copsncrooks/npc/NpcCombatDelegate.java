package me.luckyraven.copsncrooks.npc;

import lombok.CustomLog;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.downed.DownedPlayerRegistry;
import me.luckyraven.core.timer.SequenceTimer;
import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.events.projectile.WeaponShootEvent;
import me.luckyraven.weapon.raytrace.WeaponRaytracer;
import me.luckyraven.weapon.raytrace.WeaponShooting;
import me.luckyraven.weapon.types.gun.GunWeapon;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Encapsulates all combat logic for an {@link AbstractNpc}: attack dispatch, weapon firing (all {@link SelectiveFire}
 * modes), reload scheduling, aim/facing, cooldown scaling, reaction time, and equipment helpers.
 * <p>
 * Created and owned by AbstractNpc — not a bean. Accesses weapon/cooldown/difficulty state on the owner via
 * same-package field access.
 */
@CustomLog
final class NpcCombatDelegate {

	private final AbstractNpc owner;

	/**
	 * Last target engaged — used to detect a fresh acquisition for reaction-time delay.
	 */
	private LivingEntity previousAttackTarget;

	NpcCombatDelegate(AbstractNpc owner) {
		this.owner = owner;
	}

	// ── Attack dispatch ──────────────────────────────────────────────────────

	/**
	 * Attacks the target player using the highest-priority available attack: gangland weapon → vanilla ranged → melee
	 * fallback.
	 */
	void attack(Player player, boolean canUseWeapons, double attackDamage) {
		if (player.isDead() || DownedPlayerRegistry.isDowned(player.getUniqueId())) return;

		applyReactionTimeOnTargetSwitch(player);

		if (!canAttack()) return;

		faceTarget(player);

		if (canUseWeapons && owner.heldWeapon != null && hasLineOfSight(player)) {
			performGanglandWeaponAttack();
			return;
		}

		if (canUseWeapons && isHoldingVanillaRangedWeapon() && hasLineOfSight(player)) {
			performVanillaRangedAttack(player, attackDamage);
			return;
		}

		// Melee fallback still needs a clear line of sight — without this, an NPC that holds
		// a ranged weapon but fails the LOS checks above (wall between it and the target)
		// would fall through to performMeleeAttack and leak knockback onto the player through
		// the wall even though no damage lands.
		if (!hasLineOfSight(player)) return;

		performMeleeAttack(player, attackDamage);
	}

	/**
	 * Attacks the target entity using the gangland weapon (if available and line of sight) or melee fallback.
	 */
	void attackEntity(LivingEntity target, boolean canUseWeapons, double attackDamage) {
		if (target.isDead()) return;

		applyReactionTimeOnTargetSwitch(target);

		if (!canAttack()) return;

		faceTargetEntity(target);

		if (canUseWeapons && owner.heldWeapon != null && hasLineOfSight(target)) {
			performGanglandWeaponAttack();
			return;
		}

		// Melee fallback still needs a clear line of sight — see attack(Player) for rationale.
		if (!hasLineOfSight(target)) return;

		performMeleeAttackOnEntity(target, attackDamage);
	}

	/**
	 * Returns whether the attack cooldown has elapsed, the NPC is not reloading, and the held weapon (if any) is not
	 * mid-reload.
	 */
	boolean canAttack() {
		if (owner.attackCooldown > 0 || owner.reloading) return false;
		return owner.heldWeapon == null || !owner.heldWeapon.isReloading();
	}

	/**
	 * Decrements the attack cooldown by one server tick.
	 */
	void decrementAttackCooldown() {
		if (owner.attackCooldown > 0) owner.attackCooldown--;
	}

	// ── Facing ───────────────────────────────────────────────────────────────

	void faceTarget(Player player) {
		Entity entity = owner.npc.getEntity();
		if (entity == null) return;

		Location shooterEye = entity.getLocation().add(0, 1.6, 0);
		Vector   direction  = player.getEyeLocation().toVector().subtract(shooterEye.toVector()).normalize();
		direction = applyAimError(direction);
		Location rotated = entity.getLocation().setDirection(direction);
		entity.teleport(rotated);
	}

	void faceTargetEntity(LivingEntity target) {
		Entity entity = owner.npc.getEntity();
		if (entity == null) return;

		Location shooterEye = entity.getLocation().add(0, 1.6, 0);
		Vector   direction  = target.getEyeLocation().toVector().subtract(shooterEye.toVector()).normalize();
		direction = applyAimError(direction);
		Location rotated = entity.getLocation().setDirection(direction);
		entity.teleport(rotated);
	}

	// ── Equipment helpers ────────────────────────────────────────────────────

	void refreshHeldItem() {
		if (owner.heldWeapon == null) return;

		Entity entity = owner.npc.getEntity();
		if (!(entity instanceof LivingEntity livingEntity)) return;

		EntityEquipment equipment = livingEntity.getEquipment();
		if (equipment == null) return;

		ItemStack current = equipment.getItemInMainHand();
		if (current.getType() == Material.AIR) return;

		ItemBuilder builder = new ItemBuilder(current);
		owner.heldWeapon.updateWeaponData(builder);
		equipment.setItemInMainHand(builder.build());
	}

	boolean isHoldingVanillaRangedWeapon() {
		Entity entity = owner.npc.getEntity();
		if (!(entity instanceof LivingEntity livingEntity)) return false;

		EntityEquipment equipment = livingEntity.getEquipment();
		if (equipment == null) return false;

		ItemStack mainHand = equipment.getItemInMainHand();
		Material  type     = mainHand.getType();

		return type == Material.BOW || type == Material.CROSSBOW;
	}

	// ── Private attack helpers ───────────────────────────────────────────────

	void performGanglandWeaponAttack() {
		if (!(owner.heldWeapon instanceof GunWeapon gun)) return;

		if (owner.heldWeapon.isBroken() || owner.heldWeapon.isMagazineEmpty()) {
			triggerReload();
			return;
		}

		SelectiveFire mode = gun.getCurrentSelectiveFire();
		if (mode == null) mode = SelectiveFire.AUTO;

		switch (mode) {
			case SINGLE -> performSingleShot(gun);
			case BURST -> performBurstFire(gun);
			case AUTO -> performAutoShot(gun);
		}
	}

	void triggerReload() {
		if (owner.plugin == null || owner.heldWeapon == null) return;
		if (owner.heldWeapon.isReloading()) return;
		if (owner.heldWeapon.getReloadData() == null) return;
		owner.heldWeapon.reload(owner.plugin, null, false);
	}

	void performVanillaRangedAttack(Player player, double attackDamage) {
		LivingEntity shooter = owner.getEntity();
		if (shooter == null) return;

		World    world     = shooter.getWorld();
		Location eye       = shooter.getEyeLocation();
		Vector   direction = eye.getDirection().normalize();

		var result = world.rayTrace(eye, direction, 35.0, FluidCollisionMode.NEVER, true, 0.25,
		                            entity -> entity instanceof Player p &&
		                                      p.getUniqueId().equals(player.getUniqueId()));

		world.spawnParticle(Particle.CRIT, eye.clone().add(direction.clone().multiply(0.5)), 6, 0.05, 0.05, 0.05, 0.0);
		world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 1.6f);

		if (result != null && result.getHitEntity() != null) {
			player.damage(attackDamage, shooter);
		}

		owner.attackCooldown = scaleCooldown(15);
	}

	void performMeleeAttack(Player player, double attackDamage) {
		LivingEntity entity = owner.getEntity();
		if (entity == null) return;

		double healthBefore = player.getHealth();
		player.damage(attackDamage * owner.difficulty.getMeleeDamageMultiplier(), entity);
		owner.attackCooldown = scaleCooldown(5);

		if (player.isDead() || player.getHealth() < healthBefore) {
			Vector knockback = player.getLocation()
			                         .toVector()
			                         .subtract(entity.getLocation().toVector())
			                         .normalize()
			                         .multiply(0.3)
			                         .setY(0.1);
			player.setVelocity(player.getVelocity().add(knockback));
		}
	}

	void performMeleeAttackOnEntity(LivingEntity target, double attackDamage) {
		LivingEntity entity = owner.getEntity();
		if (entity == null) return;

		double healthBefore = target.getHealth();
		target.damage(attackDamage * owner.difficulty.getMeleeDamageMultiplier(), entity);
		owner.attackCooldown = scaleCooldown(5);

		if (target.isDead() || target.getHealth() < healthBefore) {
			Vector knockback = target.getLocation()
			                         .toVector()
			                         .subtract(entity.getLocation().toVector())
			                         .normalize()
			                         .multiply(0.3)
			                         .setY(0.1);
			target.setVelocity(target.getVelocity().add(knockback));
		}
	}

	private void performSingleShot(GunWeapon gun) {
		if (!fireSingleRound(gun)) return;

		int perShot  = Math.max(gun.getProjectileData().getPerShot(), 1);
		int cooldown = Math.max(gun.getProjectileData().getCooldown(), 1);
		owner.attackCooldown = scaleCooldown(Math.max(perShot * cooldown, 5));

		if (owner.heldWeapon.isMagazineEmpty()) {
			triggerReload();
		}
	}

	private void performAutoShot(GunWeapon gun) {
		if (!fireSingleRound(gun)) return;

		int cooldown = gun.getProjectileData().getCooldown();
		owner.attackCooldown = scaleCooldown(Math.max(cooldown, 5));

		if (owner.heldWeapon.isMagazineEmpty()) {
			triggerReload();
		}
	}

	private void performBurstFire(GunWeapon gun) {
		if (owner.plugin == null) return;

		int perShot  = Math.max(gun.getProjectileData().getPerShot(), 1);
		int cooldown = Math.max(gun.getProjectileData().getCooldown(), 1);

		int totalBurstTicks = perShot * cooldown + cooldown;
		owner.attackCooldown = scaleCooldown(Math.max(totalBurstTicks, 5));

		SequenceTimer burstTimer = new SequenceTimer(owner.plugin, 1L, 1L);

		for (int i = 0; i < perShot; i++) {
			int interval = i == 0 ? 0 : cooldown;

			burstTimer.addIntervalTaskPair(interval, timer -> {
				fireSingleRound(gun);

				if (owner.heldWeapon != null && owner.heldWeapon.isMagazineEmpty()) {
					triggerReload();
				}
			});
		}

		burstTimer.start(false);
	}

	private boolean fireSingleRound(GunWeapon gun) {
		if (owner.heldWeapon.isBroken() || owner.heldWeapon.isMagazineEmpty()) {
			triggerReload();
			return false;
		}

		boolean consumed = owner.heldWeapon.consumeShot();
		if (!consumed) {
			triggerReload();
			return false;
		}

		LivingEntity shooter = owner.getEntity();
		if (shooter == null) return false;

		WeaponShootEvent event = new WeaponShootEvent(owner.heldWeapon, shooter);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			owner.heldWeapon.addAmmunition(1);
			return false;
		}

		var registration = Bukkit.getServicesManager().getRegistration(WeaponRaytracer.class);
		if (registration != null) {
			WeaponShooting.fire(owner.plugin, registration.getProvider(), shooter, gun);
		}
		SoundConfiguration.playSoundsAtLocation(shooter.getEyeLocation(),
		                                        owner.heldWeapon.getSoundData().getShotCustom(),
		                                        owner.heldWeapon.getSoundData().getShotDefault());
		refreshHeldItem();
		return true;
	}

	private Vector applyAimError(Vector direction) {
		double error = owner.difficulty.getAimError();
		if (error <= 0) return direction;

		ThreadLocalRandom rng     = ThreadLocalRandom.current();
		double            offsetX = (rng.nextDouble() - 0.5) * error;
		double            offsetY = (rng.nextDouble() - 0.5) * error;
		double            offsetZ = (rng.nextDouble() - 0.5) * error;
		return direction.add(new Vector(offsetX, offsetY, offsetZ)).normalize();
	}

	private void applyReactionTimeOnTargetSwitch(LivingEntity target) {
		if (target == previousAttackTarget) return;
		previousAttackTarget = target;

		int reaction = owner.difficulty.getReactionTimeTicks();
		if (reaction > 0 && owner.attackCooldown < reaction) {
			owner.attackCooldown = reaction;
		}
	}

	private int scaleCooldown(int baseCooldown) {
		int scaled = (int) Math.round(baseCooldown * owner.difficulty.getFireRateMultiplier());
		return Math.max(scaled, 5);
	}

	private boolean hasLineOfSight(LivingEntity target) {
		LivingEntity entity = owner.getEntity();
		if (entity == null) return false;
		return entity.hasLineOfSight(target);
	}
}
