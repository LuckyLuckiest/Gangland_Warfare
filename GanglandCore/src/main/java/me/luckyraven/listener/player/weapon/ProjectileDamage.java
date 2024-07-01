package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.WeaponManager;
import me.luckyraven.feature.weapon.events.WeaponProjectileLaunchEvent;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class ProjectileDamage implements Listener {

	private final static Map<Integer, Weapon> weaponInstance = new ConcurrentHashMap<>();

	private final Gangland      gangland;
	private final WeaponManager weaponManager;

	public ProjectileDamage(Gangland gangland) {
		this.gangland      = gangland;
		this.weaponManager = gangland.getInitializer().getWeaponManager();
	}

	@EventHandler
	public void onProjectileLaunch(WeaponProjectileLaunchEvent event) {
		if (!(event.getProjectile().getShooter() instanceof Player)) return;

		Weapon weapon = event.getWeapon();

		if (weapon == null) return;

		int entityId = event.getProjectile().getEntityId();

		weaponInstance.put(entityId, weapon);
	}

	@EventHandler
	public void onProjectileEntityDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof LivingEntity entity)) return;
		if (!(event.getDamager() instanceof Projectile projectile)) return;
		if (entity instanceof ItemFrame || entity instanceof ArmorStand) return;

		int    projectileId = projectile.getEntityId();
		Weapon weapon       = weaponInstance.get(projectileId);

		if (weapon == null) return;

		Random random = new Random();
		double damage = random.nextDouble() < weapon.getProjectileCriticalHitChance() / 100D ?
						weapon.getProjectileDamage() + weapon.getProjectileCriticalHitDamage() :
						weapon.getProjectileDamage();

		// set the damage
		event.setDamage(weaponManager.isHeadPosition(projectile.getLocation(), entity.getLocation()) ?
						damage + weapon.getProjectileHeadDamage() :
						damage);

		// set the fire damage
		entity.setFireTicks(weapon.getProjectileFireTicks());

		// set the explosive damage
		// TODO

		weaponInstance.remove(projectileId);
	}

	@EventHandler
	public void onProjectileHit(ProjectileHitEvent event) {
		Projectile projectile   = event.getEntity();
		int        projectileId = projectile.getEntityId();

		if (!weaponInstance.containsKey(projectileId)) return;

		CountdownTimer timer = new CountdownTimer(gangland, 1, null, null, time -> {
			weaponInstance.remove(projectileId);
		});
		projectile.remove();

		timer.start(false);
	}

}
