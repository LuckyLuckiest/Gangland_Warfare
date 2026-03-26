package me.luckyraven.weapon.types.melee;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.ParticleUtil;
import me.luckyraven.weapon.dto.MeleeData;
import me.luckyraven.weapon.modifiers.ArmorPiercingModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MeleeAction {

	/**
	 * Entity UUIDs currently receiving programmatic melee damage — used to bypass the event cancel guard in
	 * WeaponInteract. Kept static so WeaponInteract.onEntityDamage can access it without holding an instance.
	 */
	public static final Set<UUID> pendingDamage = ConcurrentHashMap.newKeySet();

	private final MeleeWeapon         weapon;
	private final RecoilCompatibility recoilCompatibility;
	private final Map<UUID, Long>     cooldowns;

	public MeleeAction(MeleeWeapon weapon, RecoilCompatibility recoilCompatibility, Map<UUID, Long> cooldowns) {
		this.weapon              = weapon;
		this.recoilCompatibility = recoilCompatibility;
		this.cooldowns           = cooldowns;
	}

	/**
	 * Activates a melee swing. Spawns a slash arc regardless of hit.
	 *
	 * @return true if at least one entity was hit
	 */
	public boolean activate(Player player) {
		MeleeData data = weapon.getMeleeData();

		// empty-mag guard — only applies to melee weapons with ammo configured
		if (weapon.getReloadData() != null && weapon.isMagazineEmpty()) {
			SoundConfiguration.playSounds(player, weapon.getSoundData().getEmptyMagCustom(),
			                              weapon.getSoundData().getEmptyMagDefault());
			return false;
		}

		UUID weaponUuid = weapon.getUuid();
		long now        = System.currentTimeMillis();
		long cooldownMs = data.getCooldown() * 50L;

		Long lastSwing = cooldowns.get(weaponUuid);
		if (lastSwing != null && now - lastSwing < cooldownMs) return false;
		cooldowns.put(weaponUuid, now);

		Vector  lookDir = player.getLocation().getDirection().normalize();
		double  range   = data.getRange();
		boolean hit     = false;

		double flatBonus = weapon.getModifiersData().hasFlatDamage()
		                   ? weapon.getModifiersData().getFlatDamage().bonus() : 0.0;
		double baseDmg = data.getDamage() + flatBonus;

		ArmorPiercingModifier ap = weapon.getModifiersData().getArmorPiercing();

		for (Entity entity : player.getNearbyEntities(range, range, range)) {
			if (!(entity instanceof LivingEntity target)) continue;
			if (entity.equals(player)) continue;
			if (target.isDead()) continue;

			// only hit entities within ~60° arc in front of player
			Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
			if (lookDir.dot(toTarget) < 0.5) continue;

			if (ap != null && ap.armorBypass() > 0) {
				double armoredDmg = baseDmg * (1.0 - ap.armorBypass());
				double pierceDmg  = baseDmg * ap.armorBypass();
				pendingDamage.add(target.getUniqueId());
				target.damage(armoredDmg, player);
				if (!target.isDead() && pierceDmg > 0) {
					pendingDamage.add(target.getUniqueId());
					target.damage(pierceDmg);
				}
			} else {
				pendingDamage.add(target.getUniqueId());
				target.damage(baseDmg, player);
			}

			hit = true;

			if (target.isDead()) continue;
			if (data.getKnockback() <= 0) continue;
			Vector knockback = lookDir.clone().multiply(data.getKnockback());
			target.setVelocity(target.getVelocity().add(knockback));
		}

		// slash effect always plays on swing (not just on hit)
		ParticleUtil.spawnSlashArc(player.getLocation(), lookDir, range * 0.6);

		// swing sound and recoil always apply on swing
		SoundConfiguration.playSounds(player, weapon.getSoundData().getShotCustom(),
		                              weapon.getSoundData().getShotDefault());
		weapon.getRecoil().applyRecoil(recoilCompatibility, player);

		return hit;
	}

}
