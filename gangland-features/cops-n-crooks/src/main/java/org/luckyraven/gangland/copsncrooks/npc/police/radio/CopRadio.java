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
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.npc.radio.RadioLines;
import org.luckyraven.gangland.npc.radio.RadioSettings;
import org.luckyraven.gangland.npc.radio.RadioVoice;
import org.luckyraven.gangland.npc.radio.SquadRadio;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.npc.AbstractNpc;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.luckyraven.keystone.npc.spi.NpcSquadListener;

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

	public CopRadio(JavaPlugin plugin, CopLoader copLoader, CopRadioMessages messages) {
		this(copLoader::getLoadedProvider, messages, System::currentTimeMillis,
		     (task, ticks) -> Bukkit.getScheduler().runTaskLater(plugin, task, ticks));
	}

	CopRadio(Supplier<CopConfigProvider> provider, RadioLines lines, LongSupplier clock,
	         BiConsumer<Runnable, Long> later) {
		this.provider = provider;
		this.clock    = clock;
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
			else speak.onSignal(squad, signal, member, where);
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
