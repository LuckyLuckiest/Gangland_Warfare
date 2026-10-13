package org.luckyraven.gangland.copsncrooks.npc.police.perception;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.SightSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Darkness and crowds hide you (0.20.0, {@code Cops.Sight}): one {@link CopPerception.SightFactor} scaling every
 * cop's alert range by light level, civilian crowd and sneaking; gunfire, fire and glowing reset it to 1.
 * TDD skeleton: behaviour arrives with the green chunks.
 */
@CustomLog
public class SightModel implements CopPerception.SightFactor {

	private final Supplier<SightSettings> settings;
	private final CivilianNpcRegistry     civilians;
	private final CopManager              copManager;
	private final LongSupplier            clock;
	private final ConcurrentHashMap<UUID, Long> lastShot  = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, Long> lastDebug = new ConcurrentHashMap<>();
	private       Consumer<String>        debugSink = line -> {
		if (log.isDebugEnabled()) {
			log.debug(line);
		}
	};

	public SightModel(Supplier<SightSettings> settings, CivilianNpcRegistry civilians, CopManager copManager,
	                  LongSupplier clock) {
		this.settings   = settings;
		this.civilians  = civilians;
		this.copManager = copManager;
		this.clock      = clock;
	}

	/** Where the throttled {@code SIGHT} debug lines go (default: nowhere). */
	public void setDebugSink(Consumer<String> sink) {
		this.debugSink = sink;
	}

	@Override
	public double factor(CopNpc cop, Player target) {
		SightSettings s = settings.get();
		if (!s.enabled()) {
			return 1.0;
		}
		UUID id  = target.getUniqueId();
		long now = clock.getAsLong();
		Long shot = lastShot.get(id);
		if ((shot != null && now - shot < s.shotResetSeconds() * 1000L) || target.getFireTicks() > 0
		    || target.isGlowing()) {
			return 1.0;
		}

		int    light  = eyeLight(target);
		double lightF = ignoreDarkness(cop) || light < 0 ? 1.0 : light <= s.darkMaxLevel() ? s.darkFactor()
				: light <= s.dimMaxLevel() ? s.dimFactor() : 1.0;
		double crowdF = !target.isSprinting() && emptyHand(target) && outOfContact(target, s)
		                && civiliansNear(target, s) >= s.crowdMinCivilians() ? s.crowdFactor() : 1.0;
		double sneakF = target.isSneaking() ? s.sneakFactor() : 1.0;
		double result = Math.min(1.0, Math.max(s.minFactor(), lightF * crowdF * sneakF));

		Long last = lastDebug.get(id);
		if (last == null || now - last >= 5000L) {
			lastDebug.put(id, now);
			debugSink.accept(String.format(Locale.ROOT, "SIGHT %s factor=%.2f light=%d crowd=%.2f sneak=%.2f",
			                               target.getName(), result, light, crowdF, sneakF));
		}
		return result;
	}

	/** Records a gunshot by {@code player}: for {@code Shot_Reset_Seconds} his factor is 1. */
	public void shot(Player player) {
		lastShot.put(player.getUniqueId(), clock.getAsLong());
	}

	private static boolean ignoreDarkness(CopNpc cop) {
		return cop.getTierConfig() != null && cop.getTierConfig().ignoreDarkness();
	}

	private static int eyeLight(Player target) {
		Location eyes = target.getEyeLocation();
		if (eyes == null || eyes.getWorld() == null) {
			return -1;
		}
		Block block = eyes.getBlock();
		return block == null ? -1 : block.getLightLevel();
	}

	private static boolean emptyHand(Player target) {
		ItemStack item = target.getInventory().getItemInMainHand();
		return item == null || item.getType() == Material.AIR;
	}

	private boolean outOfContact(Player target, SightSettings s) {
		CopGroup group = copManager.groupOf(target.getUniqueId());
		return group == null || group.getSquad().millisSinceSighting() >= s.crowdOutOfContactSeconds() * 1000L;
	}

	private int civiliansNear(Player target, SightSettings s) {
		World world = target.getLocation().getWorld();
		if (world == null) {
			return 0;
		}
		Location here  = target.getLocation();
		double   r2    = s.crowdRadius() * s.crowdRadius();
		int      count = 0;
		for (CivilianNpc npc : new ArrayList<>(civilians.getActiveNpcs())) {
			Entity entity = npc.isValid() ? npc.getEntity() : null;
			if (entity == null) {
				continue;
			}
			Location at = entity.getLocation();
			if (world.equals(at.getWorld()) && at.distanceSquared(here) <= r2) {
				count++;
			}
		}
		return count;
	}
}
