package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.luckyraven.gangland.npc.BleedSettings;
import org.luckyraven.gangland.npc.BleedSpot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Redstone-block blood at random body spots of a hurt NPC. The particle is resolved by name so one jar covers
 * Spigot 1.16.5 ({@code BLOCK_CRACK}, {@code REDSTONE}) and 1.20.5+ ({@code BLOCK}, {@code DUST}).
 */
final class BleedEffect {

	private BleedEffect() {
	}

	/** Particle names tried after the configured one: the block-crack pair, then red dust. */
	private static final List<String> FALLBACKS = List.of("BLOCK_CRACK", "BLOCK", "REDSTONE", "DUST");

	/** The first of the configured name, its renamed twin and the fallbacks that {@code exists}; {@code null} if none. */
	static String resolveName(String configured, Predicate<String> exists) {
		List<String> tries = new ArrayList<>();
		String       name  = configured == null ? "" : configured.trim().toUpperCase(Locale.ROOT);
		if (!name.isEmpty()) tries.add(name);
		if (name.equals("BLOCK_CRACK")) tries.add("BLOCK");
		if (name.equals("BLOCK")) tries.add("BLOCK_CRACK");
		tries.addAll(FALLBACKS);
		for (String candidate : tries)
			if (exists.test(candidate)) return candidate;
		return null;
	}

	private static boolean particleExists(String name) {
		try {
			Particle.valueOf(name);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/** How hurt, 0 (at the hurt threshold) to 1 (no health): the share of the hurt band already lost. */
	static double severity(double health, double maxHealth, double hurtFraction) {
		double threshold = maxHealth * hurtFraction;
		if (threshold <= 0) return 0;
		return Math.max(0, Math.min(1, 1 - health / threshold));
	}

	/** Particles per spot: {@code base} at severity 0, up to double at severity 1. */
	static int amount(int base, double severity) {
		return (int) Math.round(base * (1 + Math.max(0, Math.min(1, severity))));
	}

	/** One spot, or two for a burst that is more likely the more hurt: a random pick without repeats. */
	static List<BleedSpot> pickSpots(List<BleedSpot> spots, double severity, Random random) {
		List<BleedSpot> pool = new ArrayList<>(spots);
		List<BleedSpot> out  = new ArrayList<>(2);
		int wanted = random.nextDouble() < 0.4 + 0.5 * severity ? 2 : 1;
		for (int i = 0; i < wanted && !pool.isEmpty(); i++)
			out.add(pool.remove(random.nextInt(pool.size())));
		return out;
	}

	/** Emits one burst on {@code body}: {@code scale} multiplies the count (a hit while hurt passes more than 1). */
	static void burst(LivingEntity body, BleedSettings settings, double severity, double scale, Random random) {
		if (body.getWorld() == null) return;
		String name = resolveName(settings.particle(), BleedEffect::particleExists);
		if (name == null) return;
		Particle particle = Particle.valueOf(name);
		Object   data     = data(name);
		Location base     = body.getLocation();
		int      count    = Math.max(1, (int) Math.round(amount(settings.count(), severity) * scale));
		for (BleedSpot spot : pickSpots(settings.spots(), severity, random)) {
			double[] offset = spot.offset(base.getYaw());
			Location at     = base.clone().add(offset[0], offset[1], offset[2]);
			if (data == null) body.getWorld().spawnParticle(particle, at, count, 0.08, 0.08, 0.08, 0);
			else body.getWorld().spawnParticle(particle, at, count, 0.08, 0.08, 0.08, 0, data);
		}
	}

	/** Block data for the block-crack pair, dust options for red dust, none for any other particle. */
	private static Object data(String name) {
		if (name.equals("BLOCK_CRACK") || name.equals("BLOCK")) {
			try {
				return Bukkit.createBlockData(Material.REDSTONE_BLOCK);
			} catch (RuntimeException | LinkageError e) {
				return new Particle.DustOptions(Color.RED, 1.0f);
			}
		}
		if (name.equals("REDSTONE") || name.equals("DUST")) return new Particle.DustOptions(Color.RED, 1.0f);
		return null;
	}
}
