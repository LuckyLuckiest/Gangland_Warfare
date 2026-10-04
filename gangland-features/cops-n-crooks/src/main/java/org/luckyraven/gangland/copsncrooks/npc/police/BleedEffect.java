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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

	/** AI ticks between bursts: every 4th at the hurt threshold, every tick at death's door. */
	static int interval(double severity) {
		return (int) Math.round(4 - 3 * Math.max(0, Math.min(1, severity)));
	}

	/** A resolved particle with its data; resolved once per configured name (a pure function of the name). */
	private record Resolved(Particle particle, Object data) {
	}

	/** Cached for a name that resolves to nothing, so it is not retried every burst. */
	private static final Resolved NONE = new Resolved(null, null);

	private static final Map<String, Resolved> CACHE = new ConcurrentHashMap<>();

	private static Resolved resolve(String configured) {
		Resolved found = CACHE.computeIfAbsent(configured == null ? "" : configured, key -> {
			String name = resolveName(key, BleedEffect::particleExists);
			return name == null ? NONE : new Resolved(Particle.valueOf(name), data(name));
		});
		return found == NONE ? null : found;
	}

	/** Emits one burst on {@code body}: {@code scale} multiplies the count (a hit while hurt passes more than 1). */
	static void burst(LivingEntity body, BleedSettings settings, double severity, double scale, Random random) {
		if (body.getWorld() == null) return;
		Resolved resolved = resolve(settings.particle());
		if (resolved == null) return;
		Location base  = body.getLocation();
		int      count = Math.max(1, (int) Math.round(amount(settings.count(), severity) * scale));
		for (BleedSpot spot : pickSpots(settings.spots(), severity, random)) {
			double[] offset = spot.offset(base.getYaw());
			Location at     = base.clone().add(offset[0], offset[1], offset[2]);
			try {
				spawn(body, resolved, at, count);
			} catch (IllegalArgumentException e) {
				// a valid particle that needs data we cannot give (item crack): remember the block-crack blood instead
				Resolved fallback = resolve("BLOCK_CRACK");
				if (fallback == null || fallback == resolved) return;
				CACHE.put(settings.particle() == null ? "" : settings.particle(), fallback);
				spawn(body, fallback, at, count);
			}
		}
	}

	private static void spawn(LivingEntity body, Resolved r, Location at, int count) {
		if (r.data() == null) body.getWorld().spawnParticle(r.particle(), at, count, 0.08, 0.08, 0.08, 0);
		else body.getWorld().spawnParticle(r.particle(), at, count, 0.08, 0.08, 0.08, 0, r.data());
	}

	/** Block data for the block-crack pair, dust options for red dust, none for any other particle. */
	private static Object data(String name) {
		if (name.equals("BLOCK_CRACK") || name.equals("BLOCK")) {
			try {
				Object blood = Bukkit.createBlockData(Material.REDSTONE_BLOCK);
				return blood != null ? blood : new Particle.DustOptions(Color.RED, 1.0f);
			} catch (RuntimeException | LinkageError e) {
				return new Particle.DustOptions(Color.RED, 1.0f);
			}
		}
		if (name.equals("REDSTONE") || name.equals("DUST")) return new Particle.DustOptions(Color.RED, 1.0f);
		return null;
	}
}
