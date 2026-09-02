package org.luckyraven.gangland.weapon.types.biological;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.luckyraven.gangland.compatibility.recoil.RecoilCompatibility;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.timer.RepeatingTimer;
import org.luckyraven.keystone.util.ActionBarManager;
import org.luckyraven.keystone.util.ParticleUtil;
import org.luckyraven.gangland.weapon.dto.BiologicalData;
import org.luckyraven.gangland.weapon.listener.WeaponInteract;
import org.luckyraven.gangland.weapon.raytrace.RaytraceRequest;
import org.luckyraven.gangland.weapon.raytrace.WeaponRaytracer;
import org.luckyraven.gangland.weapon.util.EmptyMagSoundGate;
import org.luckyraven.gangland.weapon.util.PotionEffectParser;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charged single-shot raytrace weapon. The player holds RMB to charge (each tier multiplies the base damage and picks
 * the matching {@code Effects_Per_Level} entry) and releases RMB to fire a forward raytrace that hits exactly one
 * target.
 *
 * <p>Release-detection is driven by {@link WeaponInteract}: when the watchdog clears
 * the held flag for this weapon's UUID, it invokes the runnable registered via {@link #getReleaseCallback(Player)},
 * which finalises the shot and clears the charge state.
 */
public class BiologicalAction {

	private final JavaPlugin                plugin;
	private final BiologicalWeapon          weapon;
	private final RecoilCompatibility       recoilCompatibility;
	private final WeaponRaytracer           raytracer;
	private final Map<UUID, RepeatingTimer> activeTasks;
	private final Map<UUID, int[]>          chargeLevels;

	public BiologicalAction(JavaPlugin plugin, BiologicalWeapon weapon, RecoilCompatibility recoilCompatibility,
	                        WeaponRaytracer raytracer, Map<UUID, RepeatingTimer> activeTasks) {
		this.plugin              = plugin;
		this.weapon              = weapon;
		this.recoilCompatibility = recoilCompatibility;
		this.raytracer           = raytracer;
		this.activeTasks         = activeTasks;
		this.chargeLevels        = new ConcurrentHashMap<>();
	}

	/**
	 * Begins charging the weapon. If a charge is already in progress for this weapon UUID nothing happens — RMB-hold
	 * keeps refreshing the held flag in {@link WeaponInteract}, but the charge timer is created exactly once per press
	 * cycle.
	 */
	public boolean start(Player player) {
		UUID weaponUuid = weapon.getUuid();

		if (activeTasks.containsKey(weaponUuid)) return false;
		if (weapon.getAmmunitionData() != null && weapon.isMagazineEmpty()) {
			EmptyMagSoundGate.play(plugin, player, weapon);
			return false;
		}

		BiologicalData data = weapon.getBiologicalData();

		int[] charge = {0};
		chargeLevels.put(weaponUuid, charge);

		RepeatingTimer timer = new RepeatingTimer(plugin, 1L, time -> {
			if (time.getTickCount() % data.getChargeTimePerLevel() == 0 && charge[0] < data.getMaxChargeLevel()) {
				charge[0]++;
				ActionBarManager.send(player, "§6Charging... §e[" + "■".repeat(charge[0]) +
				                              "□".repeat(data.getMaxChargeLevel() - charge[0]) + "]");
			}
			// visual ring that grows with charge level
			ParticleUtil.spawnChargeRing(player.getLocation(), charge[0], data.getMaxChargeLevel());
		});

		timer.start(false);
		activeTasks.put(weaponUuid, timer);
		return true;
	}

	/**
	 * Returns a release runnable that fires the charged shot when invoked. {@link WeaponInteract}'s watchdog calls this
	 * once it detects RMB has been released.
	 */
	public Runnable getReleaseCallback(Player player) {
		return () -> fire(player);
	}

	private void fire(Player player) {
		UUID weaponUuid = weapon.getUuid();

		RepeatingTimer timer = activeTasks.remove(weaponUuid);
		if (timer != null) timer.stop();

		int[] charge = chargeLevels.remove(weaponUuid);
		int   level  = charge != null ? charge[0] : 0;
		if (level <= 0) return;

		if (!weapon.consumeShot()) return;

		BiologicalData data = weapon.getBiologicalData();

		// release sound and recoil
		SoundEffect.playSounds(player, weapon.getSoundData().getShotCustom(),
		                              weapon.getSoundData().getShotDefault());

		if (weapon.getRecoilData() != null) {
			weapon.getRecoil().applyRecoil(recoilCompatibility, player);
			weapon.applyPush(player);
		}

		List<PotionEffect> effects = PotionEffectParser.parseList(
				List.of(data.getEffectsPerLevel().get(Math.min(level, data.getEffectsPerLevel().size()) - 1)));

		double flatBonus = weapon.getModifiersData().hasFlatDamage() ?
		                   weapon.getModifiersData().getFlatDamage().bonus() :
		                   0.0;
		double damage = data.getBaseDamage() * level + flatBonus;

		fireRay(player, damage, effects, data);

		ActionBarManager.send(player, "&aReleased at charge level " + level);
	}

	/**
	 * Fires one ray through the unified raytracer. The {@link RaytraceRequest#getImpactHandler() impact handler}
	 * applies the parsed potion effects to a single living target — non-living hits are ignored beyond the standard
	 * {@code WeaponRaytraceImpactEvent} that the raytracer fires automatically.
	 */
	private void fireRay(Player player, double damage, List<PotionEffect> effects, BiologicalData data) {
		RaytraceRequest request = RaytraceRequest.builder()
		                                         .shooter(player)
		                                         .weapon(weapon)
		                                         .origin(player.getEyeLocation())
		                                         .direction(player.getEyeLocation().getDirection().normalize())
		                                         .maxDistance(data.getRange())
		                                         .baseDamage(damage)
		                                         .hitboxExpansion(0.3)
		                                         .maxIterations(1)
		                                         .impactHandler(event -> {
													 if (event.getHitEntity() instanceof LivingEntity target) {
														 for (PotionEffect effect : effects) {
															 target.addPotionEffect(effect);
														 }
													 }
												 })
		                                         .build();

		raytracer.fireInstant(request);
	}

}
