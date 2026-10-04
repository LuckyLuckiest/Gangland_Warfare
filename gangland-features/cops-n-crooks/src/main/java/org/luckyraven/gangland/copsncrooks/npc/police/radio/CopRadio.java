package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.npc.radio.RadioLines;
import org.luckyraven.gangland.npc.radio.RadioSettings;
import org.luckyraven.gangland.npc.radio.RadioSides;
import org.luckyraven.gangland.npc.radio.RadioVoice;
import org.luckyraven.gangland.npc.radio.SquadRadio;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.npc.AbstractNpc;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.luckyraven.keystone.npc.spi.NpcSquadListener;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The police radio: one {@link SquadRadio} speaking for every {@link CopGroup}. It voices the squads' signals, lets
 * dispatch and group leaders speak lines of their own, and turns the calls other cops would hear (a contact, a man
 * down, a backup request) into {@link RadioCall}s the {@code CopManager} answers with nearby responders. That NPC
 * hearing never depends on a player being in range.
 */
public class CopRadio {

	/** A call nearby cops answer: {@code squad} of {@code group} needs help at {@code origin}. */
	public record RadioCall(CopGroup group, NpcSquad squad, Location origin) { }

	/** How long a squad falls back to cover after its Commander goes down ({@link CopGroup#isFallingBack}). */
	// ponytail: code constant, a Commander role key when owners want to tune it
	public static final long COMMANDER_FALL_BACK_MS = 5_000;

	private final Supplier<CopConfigProvider> provider;
	private final SquadRadio                  radio;
	private final LongSupplier                clock;
	private final RadioLines                  lines;

	public CopRadio(JavaPlugin plugin, CopLoader copLoader, CopRadioMessages messages) {
		this(copLoader::getLoadedProvider, messages, System::currentTimeMillis,
		     (task, ticks) -> Bukkit.getScheduler().runTaskLater(plugin, task, ticks));
	}

	CopRadio(Supplier<CopConfigProvider> provider, RadioLines lines, LongSupplier clock,
	         BiConsumer<Runnable, Long> later) {
		this.provider = provider;
		this.clock    = clock;
		this.lines    = lines;
		this.radio    = new SquadRadio(this::settings, lines, clock, () -> ThreadLocalRandom.current().nextDouble(),
		                               later);
	}

	/** The radio's clock, in milliseconds; backup timing runs on it too. */
	public long now() {
		return clock.getAsLong();
	}

	/**
	 * The listener for {@code group}'s squads: speaks their signals, queues a {@link RadioCall} on a contact or a
	 * casualty, and on a casualty requests backup, announcing it when granted. A Commander going down (leading or not)
	 * is {@code Commander_Down} rather than {@code Leader_Down} / {@code Man_Down}, and the group falls back to cover
	 * for {@link #COMMANDER_FALL_BACK_MS}.
	 */
	public NpcSquadListener listenerFor(CopGroup group, Consumer<RadioCall> onCall) {
		RadioVoice       voice = voice(group);
		NpcSquadListener speak = radio.listener(voice);
		return (squad, signal, member, where) -> {
			boolean down = signal == NpcSquadSignal.MAN_DOWN || signal == NpcSquadSignal.LEADER_DOWN;
			if (down && member instanceof CopNpc cop && cop.getRole() != null && cop.getRole().commander())
				commanderDown(group, squad, voice, member, where);
			else if (!roleLine(squad, voice, signal, member, where)) speak.onSignal(squad, signal, member, where);
			followUps(group, squad, voice, signal, member, where);
			switch (signal) {
				case CONTACT, MAN_DOWN, LEADER_DOWN -> {
					Location origin = where != null ? where : locationOf(member);
					if (origin != null) onCall.accept(new RadioCall(group, squad, origin));
					if (signal != NpcSquadSignal.CONTACT) requestBackup(group, squad, voice, member, origin);
				}
				default -> { }
			}
		};
	}

	/** Dispatch speaking about {@code target}, heard around the target: wanted starts and escalations. */
	public boolean dispatch(CopGroup group, Player target, String key, int level, String tier) {
		return radio.say(group.getSquad(), voice(group), null, "Dispatch", key, "Dispatch_Format",
		                 target.getLocation(), null, Map.of("level", String.valueOf(level), "tier", tier));
	}

	/** The group's leader (or, with the squad empty, any live cop of the group) speaks {@code key}. */
	public boolean sayFromLeader(CopGroup group, String key) {
		AbstractNpc speaker = group.getSquad().leader();
		if (speaker == null) {
			synchronized (group.getCops()) {
				for (CopNpc cop : group.getCops())
					if (cop.getEntity() != null) {
						speaker = cop;
						break;
					}
			}
		}
		if (speaker == null) return false;
		return radio.say(group.getSquad(), voice(group), speaker.getEntity(), callsign(speaker), key, "Format", null,
		                 null, Map.of());
	}

	/** {@code cop} answers a call from {@code squad}. */
	public boolean respond(CopGroup group, NpcSquad squad, CopNpc cop) {
		return radio.say(squad, voice(group), cop.getEntity(), callsign(cop), "Responding", "Format", null, null,
		                 Map.of());
	}

	/**
	 * {@code cop} speaks {@code key} itself on its group's radio, {@code extra} filling the line (field care's
	 * {@code %member%}). Silent for a cop with no entity.
	 */
	public boolean sayAs(CopGroup group, CopNpc cop, String key, Map<String, String> extra) {
		LivingEntity self = cop.getEntity();
		if (self == null) return false;
		return radio.say(group.getSquad(), voice(group), self, callsign(cop), key, "Format", null, null, extra);
	}

	/**
	 * {@link #sayAs}, {@code steps} ack delays later and past the squad gap ({@link SquadRadio#sayLater}); silent if
	 * {@code stillRelevant} is {@code false} by then.
	 */
	public void sayAsLater(CopGroup group, CopNpc cop, String key, Map<String, String> extra, int steps,
	                       BooleanSupplier stillRelevant) {
		radio.sayLater(group.getSquad(), voice(group), cop, key, extra, steps, stillRelevant);
	}

	/** Who {@code squad} of {@code group} hunts: the wanted player, or the attacker the squad was opened for. */
	public @Nullable LivingEntity huntedOf(CopGroup group, NpcSquad squad) {
		return voice(group).hunted(squad);
	}

	/**
	 * The cop's own callsign without colours ({@code "Officer Bob #1592"}, the same text as its hologram line), or
	 * {@code "SWAT-17"} for a cop without one: the tier's display name without colours, then the Citizens id.
	 */
	public static String callsign(AbstractNpc npc) {
		if (npc instanceof CopNpc cop && cop.getCallsign() != null) {
			String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', cop.getCallsign()));
			if (plain != null) return plain;
		}
		String tier = npc instanceof CopNpc cop && cop.getTierConfig() != null ? tierName(cop.getTierConfig()) : "Unit";
		return tier + "-" + npc.getNpc().getId();
	}

	/** The tier's display name without colours; empty for no tier. */
	public static String tierName(@Nullable CopTierConfig tier) {
		if (tier == null || tier.displayName() == null) return "";
		String plain = ChatColor.stripColor(GanglandChatUtil.color(tier.displayName()));
		return plain != null ? plain : "";
	}

	/** The squad flavours a role's radio lines by: its own words for a few signals (see {@link #roleKey}). */
	enum RoleKind { COMMANDER, MEDIC, DEFENDER, MARKSMAN, ASSAULT }

	/**
	 * What a role does in the squad, for its radio lines: the {@code Medic}/{@code Commander} flags and a block cone
	 * (a Defender) read from the role itself, a Marksman or Assault by the role's name; {@code null} (generic lines)
	 * for any other role, so an owner's custom role still speaks the ordinary lines.
	 */
	static @Nullable RoleKind kindOf(@Nullable CopRole role) {
		if (role == null) return null;
		if (role.commander()) return RoleKind.COMMANDER;
		if (role.medic()) return RoleKind.MEDIC;
		if (role.blockFraction() > 0) return RoleKind.DEFENDER;
		return switch (role.name().toLowerCase(Locale.ROOT)) {
			case "marksman" -> RoleKind.MARKSMAN;
			case "assault" -> RoleKind.ASSAULT;
			default -> null;
		};
	}

	/** The line kind a role says instead of the generic one for {@code signal}, or {@code null} for the generic. */
	static @Nullable String roleKey(@Nullable RoleKind kind, NpcSquadSignal signal) {
		if (kind == null) return null;
		return switch (kind) {
			case MARKSMAN -> switch (signal) {
				case ENGAGE -> "Overwatch_Set";
				case CONTACT -> "Marksman_Spotted";
				case CONTACT_LOST -> "Marksman_Lost";
				case RELOADING -> "Marksman_Reloading";
				default -> null;
			};
			case DEFENDER -> signal == NpcSquadSignal.ENGAGE ? "Shield_Up" : null;
			default -> null;
		};
	}

	/**
	 * A Marksman or Defender speaks its own line for the signal. {@code true} when the role has a line pool for it
	 * (spoken or throttled), {@code false} to fall back to the generic line.
	 */
	private boolean roleLine(NpcSquad squad, RadioVoice voice, NpcSquadSignal signal, AbstractNpc member,
	                         @Nullable Location where) {
		String key = member instanceof CopNpc cop ? roleKey(kindOf(cop.getRole()), signal) : null;
		if (key == null || lines.lines(key).isEmpty()) return false;

		radio.say(squad, voice, member.getEntity(), callsign(member), key, "Format", where, null, roleExtras(member));
		return true;
	}

	/**
	 * What the rest of the squad says in answer to a signal, one ack delay later and past the gaps: the Commander
	 * orders the squad on an engage, checks status when contact is lost and pulls the squad back on a man down; an
	 * Assault cop ordered to a flank calls its move. Each fires on the signal's edge once, and every kind has its own
	 * cooldown.
	 */
	private void followUps(CopGroup group, NpcSquad squad, RadioVoice voice, NpcSquadSignal signal,
	                       AbstractNpc member, @Nullable Location where) {
		switch (signal) {
			case ENGAGE -> commanderSays(group, squad, voice, "Commander_Orders", where, Map.of());
			case CONTACT_LOST -> commanderSays(group, squad, voice, "Status_Check", where, Map.of());
			case MAN_DOWN, LEADER_DOWN -> commanderSays(group, squad, voice, "Pull_Back", where,
			                                            Map.of("member", callsign(member)), member);
			case FLANK_LEFT, FLANK_RIGHT -> {
				AbstractNpc leader = squad.leader();
				if (leader == null || leader == member || !(member instanceof CopNpc cop) ||
				    kindOf(cop.getRole()) != RoleKind.ASSAULT) return;
				// step 2: past the leader's order (step 0) and the member's own ack (step 1)
				Map<String, String> extra = new HashMap<>(roleExtras(member));
				extra.put("direction", directionTo(member, where));
				radio.sayLater(squad, voice, member, "Flanking", extra, 2, member::isValid);
			}
			default -> { }
		}
	}

	private void commanderSays(CopGroup group, NpcSquad squad, RadioVoice voice, String key, @Nullable Location where,
	                           Map<String, String> extra, AbstractNpc... except) {
		CopNpc commander = liveCommander(group);
		if (commander == null || Arrays.asList(except).contains(commander)) return;

		Map<String, String> merged = new HashMap<>(roleExtras(commander));
		merged.putAll(extra);
		merged.put("direction", directionTo(commander, where));
		radio.sayLater(squad, voice, commander, key, merged, 1, commander::isValid);
	}

	private static @Nullable CopNpc liveCommander(CopGroup group) {
		synchronized (group.getCops()) {
			for (CopNpc cop : group.getCops())
				if (cop.isValid() && cop.getEntity() != null && kindOf(cop.getRole()) == RoleKind.COMMANDER) return cop;
		}
		return null;
	}

	/** {@code %role%}: the speaker's role key ({@code Medic}, {@code Marksman}), empty for a role-less cop. */
	private static Map<String, String> roleExtras(AbstractNpc npc) {
		CopRole role = npc instanceof CopNpc cop ? cop.getRole() : null;
		return Map.of("role", role != null ? role.name() : "");
	}

	/** The compass word ({@code Compass} lines) from {@code from} to {@code where}; empty with either missing. */
	private String directionTo(AbstractNpc from, @Nullable Location where) {
		LivingEntity self = from.getEntity();
		List<String> compass = lines.lines("Compass");
		if (self == null || where == null || compass.isEmpty() || where.getWorld() == null ||
		    !where.getWorld().equals(self.getWorld())) return "";
		return compass.get(RadioSides.compass8(self.getLocation(), where) % compass.size());
	}

	/** {@code body}'s health as a whole percent of its max, for {@code %health%}. */
	public static String percent(LivingEntity body) {
		double max = body.getMaxHealth();
		return String.valueOf(max <= 0 ? 0 : Math.round(body.getHealth() / max * 100));
	}

	/** The squad's new leader radios the Commander's fall; everyone falls back briefly ({@code CopRetreat}). */
	private void commanderDown(CopGroup group, NpcSquad squad, RadioVoice voice, AbstractNpc commander,
	                           @Nullable Location where) {
		group.setFallBackUntil(now() + COMMANDER_FALL_BACK_MS);
		AbstractNpc speaker = squad.leader();
		if (speaker == null) return;
		radio.say(squad, voice, speaker.getEntity(), callsign(speaker), "Commander_Down", "Format", where, null,
		          Map.of("member", callsign(commander)));
	}

	private void requestBackup(CopGroup group, NpcSquad squad, RadioVoice voice, AbstractNpc downed,
	                           @Nullable Location where) {
		CopConfigProvider cfg    = provider.get();
		BackupSettings    backup = cfg != null ? cfg.getBackupSettings() : BackupSettings.DEFAULT;
		if (!group.requestBackup(now(), backup)) return;

		AbstractNpc speaker = squad.leader() != null ? squad.leader() : downed;
		radio.say(squad, voice, speaker == downed ? null : speaker.getEntity(), callsign(speaker), "Backup", "Format",
		          where, null, Map.of());
	}

	private RadioSettings settings() {
		CopConfigProvider cfg = provider.get();
		return cfg != null ? cfg.getRadioSettings() : CopConfigProvider.COP_RADIO_DEFAULTS;
	}

	private static RadioVoice voice(CopGroup group) {
		return new RadioVoice() {
			@Override
			public String callsign(AbstractNpc npc) {
				return CopRadio.callsign(npc);
			}

			@Override
			public @Nullable LivingEntity hunted(NpcSquad squad) {
				UUID id = squad == group.getSquad() ? group.getTargetPlayerId() : group.attackerOf(squad);
				if (id == null) return null;
				Player player = Bukkit.getPlayer(id);
				if (player != null) return player;
				Entity entity = Bukkit.getEntity(id);
				return entity instanceof LivingEntity living ? living : null;
			}

			@Override
			public Map<String, String> extras(NpcSquad squad) {
				return Map.of("tier", group.getTierName(), "level", String.valueOf(group.getLevel()));
			}
		};
	}

	private static @Nullable Location locationOf(AbstractNpc npc) {
		LivingEntity entity = npc.getEntity();
		return entity != null ? entity.getLocation() : null;
	}
}
