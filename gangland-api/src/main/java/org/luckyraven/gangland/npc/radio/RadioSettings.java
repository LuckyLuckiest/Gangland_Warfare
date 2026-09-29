package org.luckyraven.gangland.npc.radio;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;
import org.luckyraven.keystone.persistence.config.Severity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Radio delivery tuning for one squad-radio feature (cop radio, gang shouts): who hears a line, how often, and the
 * click played alongside it. Gangland's own config surface — Keystone itself reads no YAML.
 *
 * @param enabled       {@code false} silences player-facing lines; NPCs still respond to each other through the
 *                      squad signals that drive them.
 * @param range         blocks a player must be within the speaker (or the addressee) to hear a line.
 * @param targetRange   blocks the hunted player hears their own pursuers from.
 * @param squadGapMs    minimum gap between two lines from one squad; priority lines and acks skip it.
 * @param playerGapMs   minimum gap between two low-priority lines reaching one player, across every squad.
 * @param ackDelayTicks ticks before an ordered member acknowledges a delivered order; always at least
 *                      {@code Player_Gap_Ticks + 5} so a squad's own player gap can never swallow its own ack.
 * @param responderMax  nearby members a radio call pulls in (0 = none); read here for convenience, acted on by the
 *                      consumer's own responder mechanism, not by {@link SquadRadio}.
 * @param cooldownMs    minimum gap between two lines of the same key from one squad, keyed by the line key (e.g.
 *                      {@code "Contact"}); a key absent from this map has no cooldown.
 * @param priority      keys that skip the squad and player gaps (their own cooldown still applies).
 * @param soundName     vanilla sound key played to each listener with every line, or {@code null} for none.
 * @param volume        the sound's volume.
 * @param pitch         the sound's pitch.
 * @since 1.13.0
 */
public record RadioSettings(boolean enabled, double range, double targetRange, long squadGapMs, long playerGapMs,
                             long ackDelayTicks, int responderMax, Map<String, Long> cooldownMs,
                             Set<String> priority, @Nullable String soundName, float volume, float pitch) {

	private static final long MS_PER_TICK = 50L;

	/** The configured cooldown for {@code key}, or {@code 0} when the key has none. */
	public long cooldownFor(String key) {
		return cooldownMs.getOrDefault(key, 0L);
	}

	/** Whether {@code key} skips the squad and player gaps. */
	public boolean isPriority(String key) {
		return priority.contains(key);
	}

	/**
	 * Reads {@code Enabled}, {@code Range}, {@code Target_Range}, {@code Squad_Gap_Ticks}, {@code Player_Gap_Ticks},
	 * {@code Ack_Delay_Ticks}, {@code Responder_Max}, {@code Priority} (list), {@code Cooldown_Ticks} (map) and
	 * {@code Sound.Name}/{@code Sound.Volume}/{@code Sound.Pitch} from {@code node}, falling back to
	 * {@code defaults} key by key when {@code node} is {@code null} or a key is absent. A negative tick, range or
	 * count value is clamped to {@code 0} and reported, rather than falling back to the default. {@code
	 * ackDelayTicks} is then clamped to at least {@code Player_Gap_Ticks + 5} and reported if that moved it.
	 */
	public static RadioSettings read(@Nullable NodeReader node, ConfigReport report, RadioSettings defaults) {
		if (node == null) return defaults;

		boolean enabled     = node.get("Enabled").asBool().orDefault(defaults.enabled());
		double  range       = readClampedDouble(node, report, "Range", defaults.range());
		double  targetRange = readClampedDouble(node, report, "Target_Range", defaults.targetRange());

		long squadGapTicks  = readClampedInt(node, report, "Squad_Gap_Ticks", (int) (defaults.squadGapMs() / MS_PER_TICK));
		long playerGapTicks = readClampedInt(node, report, "Player_Gap_Ticks", (int) (defaults.playerGapMs() / MS_PER_TICK));
		long ackDelayTicks  = readClampedInt(node, report, "Ack_Delay_Ticks", (int) defaults.ackDelayTicks());
		int  responderMax   = readClampedInt(node, report, "Responder_Max", defaults.responderMax());

		long minAckDelay = playerGapTicks + 5;
		if (ackDelayTicks < minAckDelay) {
			report.add(Severity.WARNING, node.mapping().location(), path(node, "Ack_Delay_Ticks"),
			           "Ack_Delay_Ticks " + ackDelayTicks + " below Player_Gap_Ticks + 5, clamped to " + minAckDelay,
			           "config.range");
			ackDelayTicks = minAckDelay;
		}

		Set<String> priority = node.has("Priority")
		                       ? new LinkedHashSet<>(node.get("Priority").asList().ofStrings().orEmpty())
		                       : defaults.priority();

		Map<String, Long> cooldownMs = readCooldowns(node, report, defaults.cooldownMs());

		MappingNode soundMapping = node.get("Sound").asMapping().orNull();
		String      soundName;
		float       volume;
		float       pitch;
		if (soundMapping != null) {
			NodeReader sound = NodeReader.of(soundMapping, report);
			soundName = sound.get("Name").asString().orDefault(defaults.soundName());
			volume    = (float) sound.get("Volume").asDouble().orDefault(defaults.volume());
			pitch     = (float) sound.get("Pitch").asDouble().orDefault(defaults.pitch());
		} else {
			soundName = defaults.soundName();
			volume    = defaults.volume();
			pitch     = defaults.pitch();
		}

		return new RadioSettings(enabled, range, targetRange, squadGapTicks * MS_PER_TICK,
		                         playerGapTicks * MS_PER_TICK, ackDelayTicks, responderMax, cooldownMs, priority,
		                         soundName, volume, pitch);
	}

	private static Map<String, Long> readCooldowns(NodeReader node, ConfigReport report, Map<String, Long> fallback) {
		if (!node.has("Cooldown_Ticks")) return fallback;

		MappingNode mapping = node.get("Cooldown_Ticks").asMapping().orNull();
		if (mapping == null) return fallback;

		NodeReader         cooldowns = NodeReader.of(mapping, report);
		Map<String, Long> result    = new LinkedHashMap<>(fallback); // key by key: unnamed keys keep their default
		for (String key : cooldowns.keys()) {
			int ticks = readClampedInt(cooldowns, report, key, 0);
			result.put(key, ticks * MS_PER_TICK);
		}
		return result;
	}

	private static int readClampedInt(NodeReader node, ConfigReport report, String key, int fallback) {
		NodeReader.NodeAccess access = node.get(key);
		int                   value  = access.asInt().orDefault(fallback);
		if (value < 0) {
			report.add(Severity.WARNING, access.node() != null ? access.node().location() : node.mapping().location(),
			           path(node, key), "negative value " + value + " clamped to 0", "config.range");
			return 0;
		}
		return value;
	}

	private static double readClampedDouble(NodeReader node, ConfigReport report, String key, double fallback) {
		NodeReader.NodeAccess access = node.get(key);
		double                value  = access.asDouble().orDefault(fallback);
		if (value < 0) {
			report.add(Severity.WARNING, access.node() != null ? access.node().location() : node.mapping().location(),
			           path(node, key), "negative value " + value + " clamped to 0", "config.range");
			return 0;
		}
		return value;
	}

	private static String path(NodeReader node, String key) {
		String base = node.mapping().path();
		return base == null || base.isEmpty() ? key : base + "." + key;
	}
}
