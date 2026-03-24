package me.luckyraven.weapon.types.biological;

import com.cryptomorin.xseries.XPotion;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.dto.BiologicalData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BiologicalAction {

	private static final Map<UUID, int[]> chargeLevels = new ConcurrentHashMap<>();

	/**
	 * Starts charging. If already charging, fires at current charge level and resets.
	 */
	public static void start(JavaPlugin plugin, Player player, Weapon weapon, Map<UUID, RepeatingTimer> activeTasks) {
		UUID weaponUuid = weapon.getUuid();

		if (activeTasks.containsKey(weaponUuid)) {
			fire(player, weapon, activeTasks);
			return;
		}

		BiologicalData data = weapon.getBiologicalData();
		if (data == null) return;

		int[] charge = {0};
		chargeLevels.put(weaponUuid, charge);

		RepeatingTimer timer = new RepeatingTimer(plugin, 1L, time -> {
			if (time.getTickCount() % data.getChargeTimePerLevel() == 0 && charge[0] < data.getMaxChargeLevel()) {
				charge[0]++;
				ChatUtil.sendActionBar(player, "§6Charging... §e[" + "■".repeat(charge[0]) +
											   "□".repeat(data.getMaxChargeLevel() - charge[0]) + "]");
			}
		});

		timer.start(false);
		activeTasks.put(weaponUuid, timer);
	}

	public static void fire(Player player, Weapon weapon, Map<UUID, RepeatingTimer> activeTasks) {
		UUID weaponUuid = weapon.getUuid();

		RepeatingTimer timer = activeTasks.remove(weaponUuid);
		if (timer != null) timer.stop();

		BiologicalData data = weapon.getBiologicalData();
		if (data == null) return;

		int[] charge = chargeLevels.remove(weaponUuid);
		int   level  = charge != null ? charge[0] : 0;
		if (level <= 0) return;

		List<PotionEffect> effects = parseEffects(data, level);
		double             radius  = data.getAreaRadius();

		for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
			if (!(entity instanceof LivingEntity target)) continue;
			if (entity.equals(player)) continue;
			if (target.getLocation().distanceSquared(player.getLocation()) > radius * radius) continue;
			for (PotionEffect effect : effects) target.addPotionEffect(effect);
		}

		ChatUtil.sendActionBar(player, "&aReleased at charge level " + level);
	}

	public static void stop(Weapon weapon, Map<UUID, RepeatingTimer> activeTasks) {
		RepeatingTimer timer = activeTasks.remove(weapon.getUuid());
		if (timer != null) timer.stop();
		chargeLevels.remove(weapon.getUuid());
	}

	private static List<PotionEffect> parseEffects(BiologicalData data, int level) {
		List<PotionEffect> effects = new ArrayList<>();
		List<String>       list    = data.getEffectsPerLevel();

		if (list == null || list.isEmpty() || level > list.size()) return effects;

		String   entry  = list.get(level - 1);
		String[] tokens = entry.split(",");

		for (String token : tokens) {
			String[] parts = token.trim().split("-");
			if (parts.length < 2) continue;

			PotionEffectType type = XPotion.of(parts[0].trim()).map(XPotion::getPotionEffectType).orElse(null);
			if (type == null) continue;

			int duration  = Integer.parseInt(parts[1].trim());
			int amplifier = parts.length >= 3 ? Integer.parseInt(parts[2].trim()) - 1 : 0;

			effects.add(new PotionEffect(type, duration, amplifier));
		}

		return effects;
	}

}
