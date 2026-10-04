package org.luckyraven.gangland.npc.radio;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.npc.AbstractNpc;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.luckyraven.keystone.npc.spi.NpcSquadListener;
import org.luckyraven.keystone.sound.SoundEffect;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Turns an {@link NpcSquad}'s signals (and a consumer's own direct calls) into range-limited, throttled chat lines:
 * one instance per feature (cop radio, gang shouts), no static state. {@link #listener} hands back an
 * {@link NpcSquadListener} to install on a squad with {@link NpcSquad#setListener}; {@link #say} is the direct path
 * for lines a consumer speaks itself (dispatch, backup, resisting, ...).
 * <p>
 * Every line clears the same gauntlet ({@link #speak}): disabled or an empty line pool silences it outright; a
 * line that is neither priority, nor an ack, nor a {@link #sayLater} follow-up respects the squad gap (only a
 * delivered non-priority line restarts it); every line (including priority ones, acks and follow-ups) respects its
 * own per-key cooldown; and, per listening player, a non-priority line respects the player gap. Voice extras and
 * call extras fill both the line and its format. Acks and follow-ups are scheduled speaks, timed on purpose; nothing
 * is queued for a retry — a line throttled when it is spoken is dropped.
 *
 * @since 1.13.0
 */
public final class SquadRadio {

	/** Before any line, so the first line from a fresh squad or a fresh listener is never throttled. */
	private static final long NEVER = Long.MIN_VALUE / 2;

	/** A route goal this many blocks above the member counts as "up high". */
	private static final double ELEVATED_BLOCKS = 2.0;

	/**
	 * Lines whose cooldown runs per speaker rather than per squad: each unit reports its own state once (a second
	 * wounded cop must still call itself in within the first one's cooldown).
	 */
	private static final Set<String> PER_SPEAKER_KEYS = Set.of("Hit");

	/** {@link #lastHeard} is pruned once it grows past this many entries. */
	private static final int PRUNE_ABOVE = 256;

	/** How stale a {@link #lastHeard} entry must be before a prune sweep drops it. */
	private static final long STALE_HORIZON_MS = 300_000L;

	private final Supplier<RadioSettings>   settings;
	private final RadioLines                lines;
	private final LongSupplier              clock;
	private final DoubleSupplier            rng;
	private final BiConsumer<Runnable, Long> later;

	private final Map<NpcSquad, SquadState> states    = new WeakHashMap<>();
	private final Map<UUID, Long>           lastHeard = new HashMap<>();

	public SquadRadio(Supplier<RadioSettings> settings, RadioLines lines, LongSupplier clock, DoubleSupplier rng,
	                  BiConsumer<Runnable, Long> later) {
		this.settings = settings;
		this.lines    = lines;
		this.clock    = clock;
		this.rng      = rng;
		this.later    = later;
	}

	/** An {@link NpcSquadListener} that speaks {@code voice}'s squad's signals; install with {@code squad.setListener}. */
	public NpcSquadListener listener(RadioVoice voice) {
		return (squad, signal, member, where) -> onSignal(voice, squad, signal, member, where);
	}

	/**
	 * Speaks a line directly, outside the signal flow (dispatch, backup, resisting, ...).
	 *
	 * @return {@code true} only when at least one player received the line.
	 */
	public boolean say(NpcSquad squad, RadioVoice voice, @Nullable LivingEntity speaker, String callsign, String key,
	                   String formatKey, @Nullable Location where, @Nullable LivingEntity addressee,
	                   Map<String, String> extra) {
		return speak(settings.get(), voice, squad, speaker, callsign, key, formatKey, where, addressee, extra, false,
		             false);
	}

	/**
	 * Speaks a follow-up line {@code steps} ack delays ({@code Ack_Delay_Ticks}) from now and past the squad gap, the
	 * way an ack follows its order: a line said in the same moment as another (a medic answering a cop's "hit") would
	 * otherwise be swallowed by the squad and player gaps. A follow-up skips the player gap too: it is scheduled on
	 * purpose, so the line that set it up cannot swallow it. Steps still order a chain of follow-ups (1, 2, ...). Silent when {@code speaker} is no longer valid by then, or {@code stillRelevant}
	 * says the moment has passed (a medic's patient killed during the delay); live settings apply.
	 */
	public void sayLater(NpcSquad squad, RadioVoice voice, AbstractNpc speaker, String key, Map<String, String> extra,
	                     int steps, BooleanSupplier stillRelevant) {
		later.accept(() -> {
			if (speaker.isValid() && stillRelevant.getAsBoolean()) {
				speak(settings.get(), voice, squad, speaker.getEntity(), voice.callsign(speaker), key, "Format", null,
				      null, extra, true, true);
			}
		}, settings.get().ackDelayTicks() * steps);
	}

	/** {@code CONTACT_LOST} -> {@code "Contact_Lost"}, and so on, for every current and future signal value. */
	public static String keyOf(NpcSquadSignal signal) {
		String[]      parts  = signal.name().split("_");
		StringBuilder result = new StringBuilder();
		for (String part : parts) {
			if (result.length() > 0) result.append('_');
			result.append(part.charAt(0)).append(part.substring(1).toLowerCase(Locale.ROOT));
		}
		return result.toString();
	}

	private void onSignal(RadioVoice voice, NpcSquad squad, NpcSquadSignal signal, AbstractNpc member,
	                      @Nullable Location where) {
		RadioSettings cfg = settings.get();
		String        key = keyOf(signal);

		switch (signal) {
			case FLANK_LEFT, FLANK_RIGHT, PUSH, SEARCH, NO_ROUTE -> orderFrom(cfg, voice, squad, member, where, key);
			case MAN_DOWN, LEADER_DOWN -> {
				AbstractNpc speaker = squad.leader();
				if (speaker == null) return;
				speak(cfg, voice, squad, speaker.getEntity(), voice.callsign(speaker), key, "Format", where, null,
				     Map.of("member", voice.callsign(member)), false, false);
			}
			// A route is neutral ("Route") unless its goal is well above the member ("Route_High"); the ladder line is
			// CLIMB's own ("Climb"), so it plays only when a climb really starts.
			case ROUTE -> speak(cfg, voice, squad, member.getEntity(), voice.callsign(member),
			                    isElevated(member, where) ? "Route_High" : key, "Format", where, null, Map.of(),
			                    false, false);
			default -> speak(cfg, voice, squad, member.getEntity(), voice.callsign(member), key, "Format", where,
			                 null, Map.of(), false, false);
		}
	}

	private static boolean isElevated(AbstractNpc member, @Nullable Location goal) {
		LivingEntity entity = member.getEntity();
		return entity != null && goal != null && goal.getY() - entity.getLocation().getY() >= ELEVATED_BLOCKS;
	}

	private void orderFrom(RadioSettings cfg, RadioVoice voice, NpcSquad squad, AbstractNpc member,
	                       @Nullable Location where, String key) {
		AbstractNpc leader = squad.leader();
		if (leader == null || leader == member) return; // a leader never orders itself; no bare "Ack" either.

		String  memberCallsign = voice.callsign(member);
		boolean delivered = speak(cfg, voice, squad, leader.getEntity(), voice.callsign(leader), key, "Format", where,
		                          member.getEntity(), Map.of("member", memberCallsign), false, false);

		if (delivered && !lines.lines("Ack").isEmpty()) {
			// Live settings when the ack fires: a reload during the delay must apply to it like to any other line.
			later.accept(() -> {
				if (member.isValid()) {
					speak(settings.get(), voice, squad, member.getEntity(), memberCallsign, "Ack", "Format", null, null,
					     Map.of(), true, false);
				}
			}, cfg.ackDelayTicks());
		}
	}

	private boolean speak(RadioSettings cfg, RadioVoice voice, NpcSquad squad, @Nullable LivingEntity speaker,
	                      String callsign, String key, String formatKey, @Nullable Location where,
	                      @Nullable LivingEntity addressee, Map<String, String> extra, boolean bypassSquadGap,
	                      boolean bypassPlayerGap) {
		List<String> pool = lines.lines(key);
		if (!cfg.enabled() || pool.isEmpty()) return false;

		boolean priority = cfg.isPriority(key);
		long    now      = clock.getAsLong();

		SquadState state = states.computeIfAbsent(squad, s -> new SquadState());
		if (!priority && !bypassSquadGap && now - state.lastLineAt < cfg.squadGapMs()) return false;
		String cooldownKey = PER_SPEAKER_KEYS.contains(key) && speaker != null ? key + "@" + speaker.getUniqueId() : key;
		if (now - state.lastByKey.getOrDefault(cooldownKey, NEVER) < cfg.cooldownFor(key)) return false;

		Location origin = speaker != null ? speaker.getLocation() : where;
		World    world  = origin != null ? origin.getWorld() : null;
		if (world == null) return false;

		LivingEntity hunted       = voice.hunted(squad);
		Location     addresseeLoc = addressee != null ? addressee.getLocation() : null;

		int    index    = pool.size() == 1 ? 0 : Math.min(pool.size() - 1, (int) (rng.getAsDouble() * pool.size()));
		String template = pool.get(index);

		Map<String, String> merged = new HashMap<>(voice.extras(squad));
		merged.putAll(extra);
		String filled = fill(template, callsign, hunted, origin, where, merged);

		List<String> formatPool = lines.lines(formatKey);
		String       format     = formatPool.isEmpty() ? "%line%" : formatPool.get(0);
		// %line% last, so text inside the filled line is never re-substituted.
		String       text = GanglandChatUtil.color(
				withExtras(format.replace("%unit%", callsign), merged).replace("%line%", filled));

		int delivered = 0;
		for (Player player : world.getPlayers()) {
			if (player.hasMetadata("NPC")) continue;

			boolean heard = withinRange(player.getLocation(), origin, cfg.range()) ||
			                (addresseeLoc != null && withinRange(player.getLocation(), addresseeLoc, cfg.range())) ||
			                (hunted instanceof Player && player.equals(hunted) &&
			                 withinRange(player.getLocation(), origin, cfg.targetRange()));
			if (!heard) continue;

			if (!priority && !bypassPlayerGap) {
				Long last = lastHeard.get(player.getUniqueId());
				if (last != null && now - last < cfg.playerGapMs()) continue;
			}

			player.sendMessage(text);
			if (cfg.soundName() != null) {
				new SoundEffect(SoundEffect.SoundType.VANILLA, cfg.soundName(), cfg.volume(), cfg.pitch())
						.playSound(player);
			}
			lastHeard.put(player.getUniqueId(), now);
			delivered++;
		}
		pruneLastHeard(now);

		if (delivered == 0) return false;
		// A priority line skips the squad gap, so it does not restart it either: the order that follows a Contact
		// in the same tick must still get through.
		if (!priority) state.lastLineAt = now;
		state.lastByKey.put(cooldownKey, now);
		return true;
	}

	private String fill(String template, String callsign, @Nullable LivingEntity hunted, Location origin,
	                    @Nullable Location where, Map<String, String> extra) {
		Location spot = where != null ? where : origin;

		// extras first: a caller-supplied %distance%/%direction% (a medic's, measured to its patient) beats the
		// speaker-to-spot one, which is 0/"north" when the line has no spot
		return withExtras(template, extra).replace("%unit%", callsign)
		                                  .replace("%target%", hunted != null ? hunted.getName() : "")
		                                  .replace("%distance%", distanceOf(origin, spot))
		                                  .replace("%direction%", directionOf(origin, spot))
		                                  .replace("%side%", sideOf(hunted, spot));
	}

	private static String withExtras(String text, Map<String, String> extra) {
		for (Map.Entry<String, String> entry : extra.entrySet()) {
			text = text.replace("%" + entry.getKey() + "%", entry.getValue());
		}
		return text;
	}

	private String distanceOf(Location origin, @Nullable Location spot) {
		if (!sameWorld(origin, spot)) return "";
		return String.valueOf(Math.round(origin.distance(spot)));
	}

	private String directionOf(Location origin, @Nullable Location spot) {
		List<String> compass = lines.lines("Compass");
		if (compass.isEmpty() || !sameWorld(origin, spot)) return "";
		return compass.get(RadioSides.compass8(origin, spot) % compass.size());
	}

	private String sideOf(@Nullable LivingEntity hunted, @Nullable Location spot) {
		List<String> sides = lines.lines("Sides");
		if (sides.isEmpty() || spot == null || !(hunted instanceof Player huntedPlayer)) return "";
		if (!sameWorld(huntedPlayer.getLocation(), spot)) return "";
		return sides.get(sideIndex(RadioSides.sideOf(huntedPlayer.getLocation(), spot)) % sides.size());
	}

	private static int sideIndex(RadioSides.Side side) {
		return switch (side) {
			case FRONT -> 0;
			case LEFT -> 1;
			case RIGHT -> 2;
			case BEHIND -> 3;
		};
	}

	private static boolean withinRange(Location a, @Nullable Location b, double range) {
		if (!sameWorld(a, b)) return false;
		return a.distanceSquared(b) <= range * range;
	}

	private static boolean sameWorld(@Nullable Location a, @Nullable Location b) {
		if (a == null || b == null) return false;
		World wa = a.getWorld();
		World wb = b.getWorld();
		return wa != null && wa.equals(wb);
	}

	// ponytail: lazy time-based prune, not a true LRU — fine since STALE_HORIZON_MS is far above any Player_Gap_Ticks.
	private void pruneLastHeard(long now) {
		if (lastHeard.size() <= PRUNE_ABOVE) return;
		lastHeard.values().removeIf(heardAt -> now - heardAt > STALE_HORIZON_MS);
	}

	private static final class SquadState {

		long              lastLineAt = NEVER;
		Map<String, Long> lastByKey  = new HashMap<>();
	}
}
