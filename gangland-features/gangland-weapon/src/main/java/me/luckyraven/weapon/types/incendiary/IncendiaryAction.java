package me.luckyraven.weapon.types.incendiary;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.utilities.ParticleUtil;
import me.luckyraven.weapon.dto.IncendiaryData;
import me.luckyraven.weapon.events.WeaponEntityDamageEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class IncendiaryAction {

	private final JavaPlugin                plugin;
	private final IncendiaryWeapon          weapon;
	private final RecoilCompatibility       recoilCompatibility;
	private final Map<UUID, RepeatingTimer> activeTasks;

	public IncendiaryAction(JavaPlugin plugin, IncendiaryWeapon weapon, RecoilCompatibility recoilCompatibility,
	                        Map<UUID, RepeatingTimer> activeTasks) {
		this.plugin              = plugin;
		this.weapon              = weapon;
		this.recoilCompatibility = recoilCompatibility;
		this.activeTasks         = activeTasks;
	}

	/**
	 * Starts continuous fire spray. Toggles off if already active. Fuel is tracked via
	 * {@code weapon.getCurrentMagCapacity()} — unlimited when no reloadData is set.
	 */
	public void start(Player player) {
		UUID weaponUuid = weapon.getUuid();

		if (activeTasks.containsKey(weaponUuid)) {
			stop();
			return;
		}

		IncendiaryData data       = weapon.getIncendiaryData();
		boolean        tracksAmmo = weapon.getAmmunitionData() != null;

		if (tracksAmmo && weapon.isMagazineEmpty()) return;

		// spray-start sound and recoil
		SoundConfiguration.playSounds(player, weapon.getSoundData().getShotCustom(),
		                              weapon.getSoundData().getShotDefault());
		weapon.getRecoil().applyRecoil(recoilCompatibility, player);

		double flatBonus = weapon.getModifiersData().hasFlatDamage() ?
		                   weapon.getModifiersData().getFlatDamage().bonus() :
		                   0.0;

		RepeatingTimer timer = new RepeatingTimer(plugin, data.getTickRate(), time -> {
			if (tracksAmmo && weapon.isMagazineEmpty()) {
				stop();
				time.stop();
				return;
			}

			if (tracksAmmo) weapon.consumeShot();

			sprayFire(player, data, flatBonus);
		});

		timer.start(false);
		activeTasks.put(weaponUuid, timer);
	}

	public void stop() {
		RepeatingTimer timer = activeTasks.remove(weapon.getUuid());
		if (timer != null) timer.stop();
	}

	private void sprayFire(Player player, IncendiaryData data, double flatBonus) {
		Location eye   = player.getEyeLocation();
		Vector   dir   = eye.getDirection().normalize();
		double   range = data.getRange();
		World    world = player.getWorld();

		// muzzle position: in front, shifted to the right hand side, slightly below eye
		Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
		if (right.lengthSquared() < 0.001) right = new Vector(1, 0, 0);
		right.normalize();
		Location muzzle = eye.clone().add(dir.clone().multiply(0.5)).add(right.multiply(0.25)).add(0, -0.15, 0);

		// flame particles from muzzle in a realistic cone with upward drift
		ParticleUtil.spawnFlameCone(muzzle, dir, range, data.getConeAngle());

		// ray-trace multiple directions within the cone to find entities accurately
		double            halfAngle = Math.toRadians(data.getConeAngle() / 2.0);
		int               rays      = Math.max(4, (int) (data.getConeAngle() / 8));
		ThreadLocalRandom rng       = ThreadLocalRandom.current();

		Vector perp1 = dir.clone().crossProduct(new Vector(0, 1, 0));
		if (perp1.lengthSquared() < 0.001) perp1 = dir.clone().crossProduct(new Vector(1, 0, 0));
		perp1.normalize();
		Vector perp2 = dir.clone().crossProduct(perp1).normalize();

		Set<LivingEntity> hit = new HashSet<>();
		for (int r = 0; r < rays; r++) {
			double theta = rng.nextDouble() * halfAngle;
			double phi   = rng.nextDouble() * 2 * Math.PI;
			Vector rayDir = dir.clone()
			                   .add(perp1.clone().multiply(Math.sin(theta) * Math.cos(phi)))
			                   .add(perp2.clone().multiply(Math.sin(theta) * Math.sin(phi)))
			                   .normalize();

			RayTraceResult result = world.rayTraceEntities(muzzle, rayDir, range, 0.3,
			                                               e -> e instanceof LivingEntity && !e.equals(player));
			if (result != null && result.getHitEntity() instanceof LivingEntity target) {
				hit.add(target);
			}
		}

		for (LivingEntity target : hit) {
			target.setFireTicks(data.getFireDuration());
			if (flatBonus > 0) {
				// apply flat bonus without attacker so it bypasses armor and the weapon event guard
				target.damage(flatBonus);
			}
		}

		// Fire WeaponEntityDamageEvent for non-living entities (vehicles) inside the spray cone
		if (flatBonus > 0) {
			for (Entity entity : player.getNearbyEntities(range, range, range)) {
				if (entity instanceof LivingEntity || entity.equals(player)) continue;
				Vector toEntity = entity.getLocation().toVector().subtract(muzzle.toVector());
				double dist     = toEntity.length();
				if (dist > range || dist < 0.001) continue;
				if (dir.dot(toEntity.normalize()) < Math.cos(halfAngle)) continue;
				Bukkit.getPluginManager().callEvent(new WeaponEntityDamageEvent(weapon, entity, flatBonus, player));
			}
		}
	}

}
