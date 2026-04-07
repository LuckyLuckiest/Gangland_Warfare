package me.luckyraven.weapon.types.incendiary;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.utilities.ParticleUtil;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.dto.IncendiaryData;
import me.luckyraven.weapon.events.projectile.WeaponRaytraceImpactEvent;
import me.luckyraven.weapon.raytrace.RaytraceRequest;
import me.luckyraven.weapon.raytrace.WeaponMuzzle;
import me.luckyraven.weapon.raytrace.WeaponRaytracer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

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
	private final WeaponRaytracer           raytracer;
	private final Map<UUID, RepeatingTimer> activeTasks;

	public IncendiaryAction(JavaPlugin plugin, WeaponService weaponService, IncendiaryWeapon weapon,
	                        RecoilCompatibility recoilCompatibility, WeaponRaytracer raytracer,
	                        Map<UUID, RepeatingTimer> activeTasks) {
		this.plugin              = plugin;
		this.weaponService       = weaponService;
		this.weapon              = weapon;
		this.recoilCompatibility = recoilCompatibility;
		this.raytracer           = raytracer;
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
		Location muzzle = WeaponMuzzle.compute(player, dir);

		double flatBonus = weapon.getModifiersData().hasFlatDamage() ?
		                   weapon.getModifiersData().getFlatDamage().bonus() :
		                   0.0;

		ParticleUtil.spawnFlameCone(muzzle, dir, data.getRange(), data.getConeAngle());

		fireCone(player, dir, data, flatBonus);
	}

	/**
	 * Sprays a cone of rays through the unified raytracer. Each ray reports its impact via a custom
	 * {@link RaytraceRequest#getImpactHandler()} that applies fire ticks to LivingEntity hits and fires
	 * {@link WeaponRaytraceImpactEvent} as the canonical hook for vehicle / NPC reactions.
	 */
	private void fireCone(Player player, Vector dir, IncendiaryData data, double flatBonus) {
		double halfAngle = Math.toRadians(data.getConeAngle() / 2.0);
		int    rays      = Math.max(4, (int) (data.getConeAngle() / 8));

		Vector perp1 = dir.clone().crossProduct(new Vector(0, 1, 0));
		if (perp1.lengthSquared() < 0.001) {
			perp1 = dir.clone().crossProduct(new Vector(1, 0, 0));
		}
		perp1.normalize();
		Vector perp2 = dir.clone().crossProduct(perp1).normalize();

		ThreadLocalRandom rng = ThreadLocalRandom.current();

		for (int r = 0; r < rays; r++) {
			double theta = rng.nextDouble() * halfAngle;
			double phi   = rng.nextDouble() * 2 * Math.PI;
			Vector rayDir = dir.clone()
			                   .add(perp1.clone().multiply(Math.sin(theta) * Math.cos(phi)))
			                   .add(perp2.clone().multiply(Math.sin(theta) * Math.sin(phi)))
			                   .normalize();

			RaytraceRequest request = RaytraceRequest.builder()
			                                         .shooter(player)
			                                         .weapon(weapon)
			                                         .origin(player.getEyeLocation())
			                                         .direction(rayDir)
			                                         .maxDistance(data.getRange())
			                                         .baseDamage(flatBonus > 0 ? flatBonus : 0.001)
			                                         .hitboxExpansion(0.3)
			                                         .maxIterations(1)
			                                         .impactHandler(
															 event -> applyIncendiaryImpact(event, data, flatBonus))
			                                         .build();

			raytracer.fireInstant(request);
		}
	}

	private void applyIncendiaryImpact(WeaponRaytraceImpactEvent event, IncendiaryData data, double flatBonus) {
		Entity hit = event.getHitEntity();
		if (hit == null) {
			return;
		}

		if (hit instanceof LivingEntity target) {
			// Always attribute damage to the shooter so getKiller() is set and the death message
			// is correctly assigned. When there is no configured flat bonus a sub-tick amount
			// (0.001) is used so the combat tracker is updated without meaningfully changing
			// health.
			target.setFireTicks(data.getFireDuration());
			double attributed = flatBonus > 0 ? flatBonus : 0.001;
			target.setNoDamageTicks(0);
			pendingDamage.add(target.getUniqueId());
			target.damage(attributed, event.getShooter());
			return;
		}

		// Non-living entity (vehicle, etc.). The unified WeaponRaytraceImpactEvent has already
		// fired with damage = flatBonus (set in the request), so CarDamageListener picks it up via
		// its WeaponRaytraceImpactEvent handler. Nothing to do here.
	}

}
