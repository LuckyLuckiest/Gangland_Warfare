package me.luckyraven.weapon.types.incendiary;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.utilities.ParticleUtil;
import me.luckyraven.weapon.WeaponService;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class IncendiaryAction {

	/**
	 * Entity UUIDs receiving attributed fire damage — bypasses WeaponInteract's cancel guard so the combat tracker
	 * stays updated (enabling death attribution and flat-damage application).
	 */
	public static final Set<UUID> pendingDamage = ConcurrentHashMap.newKeySet();

	private final JavaPlugin                plugin;
	private final WeaponService             weaponService;
	private final IncendiaryWeapon          weapon;
	private final RecoilCompatibility       recoilCompatibility;
	private final Map<UUID, RepeatingTimer> activeTasks;

	public IncendiaryAction(JavaPlugin plugin, WeaponService weaponService, IncendiaryWeapon weapon,
	                        RecoilCompatibility recoilCompatibility, Map<UUID, RepeatingTimer> activeTasks) {
		this.plugin              = plugin;
		this.weaponService       = weaponService;
		this.weapon              = weapon;
		this.recoilCompatibility = recoilCompatibility;
		this.activeTasks         = activeTasks;
	}

	/**
	 * Starts continuous fire spray. Toggles off if already active. Fuel is tracked via
	 * {@code weapon.getCurrentMagCapacity()} — unlimited when no ammunitionData is set.
	 */
	public void start(Player player) {
		UUID weaponUuid = weapon.getUuid();

		if (activeTasks.containsKey(weaponUuid)) {
			stop();
			return;
		}

		if (weapon.isBroken()) return;

		IncendiaryData data       = weapon.getIncendiaryData();
		boolean        tracksAmmo = weapon.getAmmunitionData() != null;

		if (tracksAmmo && weapon.isMagazineEmpty()) return;

		// spray-start sound
		SoundConfiguration.playSounds(player, weapon.getSoundData().getShotCustom(),
		                              weapon.getSoundData().getShotDefault());

		RepeatingTimer timer = new RepeatingTimer(plugin, data.getTickRate(), time -> {
			if (weapon.isBroken() || (tracksAmmo && weapon.isMagazineEmpty())) {
				stop();
				time.stop();
				return;
			}
			sprayFire(player, data, tracksAmmo);
		});

		timer.start(false);
		activeTasks.put(weaponUuid, timer);
	}

	public void stop() {
		RepeatingTimer timer = activeTasks.remove(weapon.getUuid());
		if (timer != null) timer.stop();
	}

	// --- Per-tick spray ---

	private void sprayFire(Player player, IncendiaryData data, boolean tracksAmmo) {
		ItemBuilder heldWeapon = weaponService.getHeldWeaponItem(player);
		if (heldWeapon == null) {
			stop();
			return;
		}

		int slot = player.getInventory().getHeldItemSlot();

		// consume fuel
		if (tracksAmmo) weapon.consumeShot();

		// update ammo counter in display name
		weapon.updateWeaponData(heldWeapon);

		// durability on shot
		short onShot = weapon.getDurabilityData().getOnShot();
		if (onShot > 0) weapon.decreaseDurability(heldWeapon, onShot);

		// push updated item to inventory
		weapon.updateWeapon(player, heldWeapon, slot);

		// recoil and push per tick
		weapon.getRecoil().applyRecoil(recoilCompatibility, player);
		weapon.applyPush(player);

		// fire spray
		Location eye    = player.getEyeLocation();
		Vector   dir    = eye.getDirection().normalize();
		Location muzzle = computeMuzzle(eye, dir);

		double flatBonus = weapon.getModifiersData().hasFlatDamage() ?
		                   weapon.getModifiersData().getFlatDamage().bonus() :
		                   0.0;

		ParticleUtil.spawnFlameCone(muzzle, dir, data.getRange(), data.getConeAngle());

		Set<LivingEntity> hit = hitScan(player, muzzle, dir, data);
		applyFireDamage(player, hit, data, flatBonus);
		applyVehicleDamage(player, muzzle, dir, data, flatBonus);
	}

	private Location computeMuzzle(Location eye, Vector dir) {
		Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
		if (right.lengthSquared() < 0.001) right = new Vector(1, 0, 0);
		right.normalize();
		return eye.clone().add(dir.clone().multiply(0.5)).add(right.multiply(0.25)).add(0, -0.15, 0);
	}

	private Set<LivingEntity> hitScan(Player player, Location muzzle, Vector dir, IncendiaryData data) {
		double halfAngle = Math.toRadians(data.getConeAngle() / 2.0);
		int    rays      = Math.max(4, (int) (data.getConeAngle() / 8));
		World  world     = player.getWorld();

		Vector perp1 = dir.clone().crossProduct(new Vector(0, 1, 0));
		if (perp1.lengthSquared() < 0.001) perp1 = dir.clone().crossProduct(new Vector(1, 0, 0));
		perp1.normalize();
		Vector perp2 = dir.clone().crossProduct(perp1).normalize();

		ThreadLocalRandom rng = ThreadLocalRandom.current();
		Set<LivingEntity> hit = new HashSet<>();

		for (int r = 0; r < rays; r++) {
			double theta = rng.nextDouble() * halfAngle;
			double phi   = rng.nextDouble() * 2 * Math.PI;
			Vector rayDir = dir.clone()
			                   .add(perp1.clone().multiply(Math.sin(theta) * Math.cos(phi)))
			                   .add(perp2.clone().multiply(Math.sin(theta) * Math.sin(phi)))
			                   .normalize();

			RayTraceResult result = world.rayTraceEntities(muzzle, rayDir, data.getRange(), 0.3,
			                                               e -> e instanceof LivingEntity && !e.equals(player));
			if (result != null && result.getHitEntity() instanceof LivingEntity target) {
				hit.add(target);
			}
		}

		return hit;
	}

	private void applyFireDamage(Player player, Set<LivingEntity> hit, IncendiaryData data, double flatBonus) {
		for (LivingEntity target : hit) {
			target.setFireTicks(data.getFireDuration());
			// Always attribute damage to the shooter so getKiller() is set and
			// the death message/kill credit is correctly assigned. When there is
			// no configured flat bonus a sub-tick amount (0.001) is used so that
			// the combat tracker is updated without meaningfully changing health.
			double attributed = flatBonus > 0 ? flatBonus : 0.001;
			target.setNoDamageTicks(0);
			pendingDamage.add(target.getUniqueId());
			target.damage(attributed, player);
		}
	}

	private void applyVehicleDamage(Player player, Location muzzle, Vector dir, IncendiaryData data, double flatBonus) {
		if (flatBonus <= 0) return;

		double halfAngle = Math.toRadians(data.getConeAngle() / 2.0);

		for (Entity entity : player.getNearbyEntities(data.getRange(), data.getRange(), data.getRange())) {
			if (entity instanceof LivingEntity || entity.equals(player)) continue;

			Vector toEntity = entity.getLocation().toVector().subtract(muzzle.toVector());
			double dist     = toEntity.length();

			if (dist > data.getRange() || dist < 0.001) continue;
			if (dir.dot(toEntity.normalize()) < Math.cos(halfAngle)) continue;

			WeaponEntityDamageEvent event = new WeaponEntityDamageEvent(weapon, entity, flatBonus, player);
			Bukkit.getPluginManager().callEvent(event);
		}
	}

}
