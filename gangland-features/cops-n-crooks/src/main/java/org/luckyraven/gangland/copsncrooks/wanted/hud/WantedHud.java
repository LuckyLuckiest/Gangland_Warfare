package org.luckyraven.gangland.copsncrooks.wanted.hud;

import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.HudSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.PostEscapeSettings;
import org.luckyraven.gangland.events.wanted.EvasionState;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The per-player chase HUD: a boss bar (red seen, yellow/white searching with an arrow out of the zone, green for a
 * lost star), the zone ring only the chased player sees, and the compass. Driven by {@link #state} events and a
 * half-second {@link #tick()}. Touches no action bar.
 *
 * @since 0.15.0
 */
public class WantedHud {

	/** A lost star shows green for this many ticks (one tick is half a second). */
	private static final int EVADED_TICKS = 6;

	/**
	 * {@code Player#spawnParticle(..., data, force)}, newer than the 1.16.5 API this compiles against; null where the
	 * server lacks it. Force lifts the client's 32-block particle cut-off, so the far side of the ring still shows.
	 */
	private static final @Nullable Method FORCED_PARTICLE = forcedParticle();

	private final ChaseConfigLoader chase;
	private final WantedMessages    messages;

	private final Map<UUID, Entry> entries = new HashMap<>();

	private long tickCount;

	public WantedHud(ChaseConfigLoader chase, WantedMessages messages) {
		this.chase    = chase;
		this.messages = messages;
	}

	/**
	 * Starts showing the HUD to {@code player}, in the SEEN state. A player already shown only gets new stars. A new wanted
	 * start takes the bar back from a post-escape bounty (0.16.1 T-187).
	 */
	public void show(Player player, int level, String stars) {
		Entry entry = entries.computeIfAbsent(player.getUniqueId(), id -> new Entry(player));

		entry.bounty = false;
		entry.amount = null;
		if (entry.bar == null && chase.get().hud().bossBar()) createBar(entry);

		entry.level = level;
		entry.stars = stars;
		render(entry);
	}

	/**
	 * The post-escape bounty (0.16.1 T-187): the bar reads the bounty and that the cops are still looking, and the HUD
	 * counts {@code beats} beats (half a second each) down to the expiry reported by {@link #tick()}. {@code amount} is read
	 * live on every beat.
	 */
	public void bounty(Player player, int beats, Supplier<BigDecimal> amount) {
		Entry entry = entries.computeIfAbsent(player.getUniqueId(), id -> new Entry(player));

		entry.bounty     = true;
		entry.beatsLeft  = beats;
		entry.beatsTotal = beats;
		entry.amount     = amount;
		entry.state      = EvasionState.SEEN;
		entry.after      = EvasionState.SEEN;

		if (!postEscape().bountyHud()) {
			removeBar(entry);
		} else if (entry.bar == null && chase.get().hud().bossBar()) {
			createBar(entry);
		}

		render(entry);
		aimCompass(entry);
	}


	/** New star count; ignored for a player who is not shown. */
	public void stars(Player player, int level, String stars) {
		Entry entry = entries.get(player.getUniqueId());
		if (entry == null) return;

		entry.level = level;
		entry.stars = stars;
		render(entry);
	}

	/** A chase state change. Ignored for a player who is not shown, so a late event never re-creates a bar. */
	public void state(Player player, EvasionState state, int level, int secondsLeft, @Nullable Location centre,
	                  double radius) {
		Entry entry = entries.get(player.getUniqueId());
		if (entry == null) return;

		entry.level       = level;
		entry.secondsLeft = secondsLeft;
		entry.centre      = centre;
		entry.radius      = radius;

		if (state == EvasionState.EVADED) {
			entry.evadedUntil = tickCount + EVADED_TICKS;
			entry.state       = state;
		} else if (entry.state == EvasionState.EVADED && tickCount < entry.evadedUntil) {
			entry.after = state;
		} else {
			entry.state = state;
			entry.after = state;
		}

		render(entry);
		aimCompass(entry);
	}

	/** How many stars the drop just shown took; the green bar names the count from 2. Ignored when not shown. */
	public void lost(Player player, int lost) {
		Entry entry = entries.get(player.getUniqueId());
		if (entry == null) return;

		entry.lost = lost;
		render(entry);
	}

	/** Removes the bar and the ring and gives the compass back. */
	public void hide(Player player) {
		Entry entry = entries.remove(player.getUniqueId());
		if (entry == null) return;

		if (entry.bar != null) entry.bar.removeAll();
		restoreCompass(entry);
	}

	/**
	 * Half-second beat: flashes the bar, ends the green flash, redraws the ring every second beat, and counts the bounty
	 * down. Returns the players whose bounty ran out on this beat; their HUD is already hidden.
	 */
	public List<Player> tick() {
		tickCount++;

		List<Player> expired = new ArrayList<>();
		for (Entry entry : entries.values()) {
			if (entry.bounty) {
				if (--entry.beatsLeft <= 0) {
					expired.add(entry.player);
					continue;
				}

				render(entry);
				aimCompass(entry);
				continue;
			}

			if (entry.state == EvasionState.EVADED && tickCount >= entry.evadedUntil) {
				entry.state = entry.after;
				entry.lost  = 1;
			}

			render(entry);
			aimCompass(entry);

			if (entry.state == EvasionState.SEARCHING && tickCount % 2 == 0) drawRing(entry);
		}

		for (Player player : expired) hide(player);
		return expired;
	}

	private void createBar(Entry entry) {
		entry.bar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
		entry.bar.addPlayer(entry.player);
	}

	private void removeBar(Entry entry) {
		if (entry.bar != null) entry.bar.removeAll();
		entry.bar = null;
	}

	/** The post-escape settings; a loader that has no settings yet (a test double) gets the shipped ones. */
	private PostEscapeSettings postEscape() {
		PostEscapeSettings settings = chase.getPostEscape();
		return settings == null ? PostEscapeSettings.DEFAULT : settings;
	}

	private void render(Entry entry) {
		if (entry.bar == null) return;

		if (entry.bounty) {
			entry.bar.setTitle(StarCard.bountyTitle(messages, entry.amount == null ? BigDecimal.ZERO : entry.amount.get()));
			entry.bar.setColor(StarCard.bountyColor(postEscape().barColor()));
			entry.bar.setProgress(entry.beatsTotal <= 0 ? 0.0
			                                            : Math.max(0.0, Math.min(1.0, (double) entry.beatsLeft / entry.beatsTotal)));
			return;
		}

		String title = StarCard.barTitle(messages, entry.state, entry.stars, entry.secondsLeft, entry.lost);
		if (showsWay(entry)) {
			title += StarCard.wayHint(messages, entry.centre, entry.radius, entry.player.getLocation());
		}

		entry.bar.setTitle(title);
		entry.bar.setColor(StarCard.barColor(entry.state, tickCount % 2 == 0));
		entry.bar.setProgress(progress(entry));
	}

	private double progress(Entry entry) {
		if (entry.state != EvasionState.SEARCHING) return 1.0;

		int total = chase.get().evasion().secondsToDropFor(entry.level);

		return total <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) entry.secondsLeft / total));
	}

	private boolean showsWay(Entry entry) {
		Location centre = entry.centre;

		return !entry.bounty && entry.state == EvasionState.SEARCHING && chase.get().hud().compass() && centre != null
		       && centre.getWorld() != null && centre.getWorld().equals(entry.player.getWorld());
	}

	private void aimCompass(Entry entry) {
		if (entry.bounty || entry.state != EvasionState.SEARCHING || !chase.get().hud().compass()
		    || entry.centre == null) {
			restoreCompass(entry);
			return;
		}

		if (!entry.compassSaved) {
			entry.savedCompass = entry.player.getCompassTarget();
			entry.compassSaved = true;
		}

		entry.player.setCompassTarget(StarCard.exitPoint(entry.centre, entry.radius, entry.player.getLocation()));
	}

	private void restoreCompass(Entry entry) {
		if (!entry.compassSaved) return;

		entry.player.setCompassTarget(entry.savedCompass);
		entry.compassSaved = false;
		entry.savedCompass = null;
	}

	// ponytail: fixed point count, the client culls far points
	private void drawRing(Entry entry) {
		HudSettings hud    = chase.get().hud();
		Location    centre = entry.centre;
		World       world  = centre == null ? null : centre.getWorld();

		if (!hud.zoneRing() || world == null || !world.equals(entry.player.getWorld())) return;

		Particle particle = resolveParticle(hud.zoneParticle());
		Object   data     = particle.getDataType() == Particle.DustOptions.class
		                    ? new Particle.DustOptions(Color.RED, 1.5F) : null;
		double   y        = entry.player.getLocation().getY() + 1;
		int      points   = Math.max(1, hud.zonePoints());

		for (int i = 0; i < points; i++) {
			double   angle = 2 * Math.PI * i / points;
			Location at    = new Location(world, centre.getX() + Math.cos(angle) * entry.radius, y,
			                              centre.getZ() + Math.sin(angle) * entry.radius);

			spawnForced(entry.player, particle, at, data);
		}
	}

	/** Sent to {@code player} alone; forced where the server supports it. */
	private static void spawnForced(Player player, Particle particle, Location at, @Nullable Object data) {
		if (FORCED_PARTICLE != null) {
			try {
				FORCED_PARTICLE.invoke(player, particle, at, 1, 0D, 0D, 0D, 0D, data, true);
				return;
			} catch (ReflectiveOperationException exception) {
				// fall back to the unforced call
			}
		}

		player.spawnParticle(particle, at, 1, 0, 0, 0, 0, data);
	}

	private static @Nullable Method forcedParticle() {
		try {
			return Player.class.getMethod("spawnParticle", Particle.class, Location.class, int.class, double.class,
			                              double.class, double.class, double.class, Object.class, boolean.class);
		} catch (NoSuchMethodException exception) {
			return null;
		}
	}

	private static Particle resolveParticle(String name) {
		try {
			Particle resolved = XParticle.valueOf(name.toUpperCase()).get();
			if (resolved != null) return resolved;
		} catch (IllegalArgumentException exception) {
			// fall through to the default
		}

		return XParticle.DUST.get();
	}

	private static final class Entry {
		private final Player player;

		private @Nullable BossBar  bar;
		private int                level;
		private String             stars = "";
		private EvasionState       state = EvasionState.SEEN;
		private EvasionState       after = EvasionState.SEEN;
		private int                secondsLeft;
		private int                lost = 1;
		private @Nullable Location centre;
		private double             radius;
		private long               evadedUntil;
		private boolean            compassSaved;
		private @Nullable Location savedCompass;
		private boolean            bounty;
		private int                beatsLeft;
		private int                beatsTotal;
		private @Nullable Supplier<BigDecimal> amount;

		private Entry(Player player) {
			this.player = player;
		}
	}
}
