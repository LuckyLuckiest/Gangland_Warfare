package org.luckyraven.gangland.copsncrooks.wanted.report;

import lombok.CustomLog;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.WitnessSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.heat.ReportKind;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.timer.RepeatingTimer;
import org.luckyraven.keystone.util.ActionBarManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Cop eyewitness reports (0.21.2, PLAN.md section 1). A gated crime (Wanted.Witnesses.Crimes) a cop saw waits
 * {@code Witnesses.Cops.Report_Seconds} while the nearest witness, glowing, radios it in; it lands as a seen-by-cop
 * commit of every witness still alive, tips the squad to the crime spot and has dispatch name that spot (R4). Every
 * witness dead first: the call never went out. No cop saw it: a civilian who did calls 911; nobody did: no star.
 * <p>
 * A death, cuff or arrest drops the criminal's reports, a restraint at landing too; a logout defers a report with a
 * witness alive (and a 911 call cut short by it) to his next join within {@code Defer_Minutes}. A 10-tick main-thread
 * task drives it all; main thread only. Known limit: a restart inside a report window forgives that crime.
 * {@code ponytail:} persist the deferred map if owners report abuse.
 *
 * @since 0.21.2
 */
@CustomLog
public final class CopCrimeReports implements BeanLifecycle {

	/** How often the task runs, in game ticks. */
	public static final long PERIOD_TICKS = 10L;

	/** {@code YamlCopConfigProvider}'s fallback when no cop config is loaded. */
	private static final double DEFAULT_ALERT_RANGE = 40.0;

	/** Clock, action bar, repeating task and debug sinks; replaced by fakes in tests. */
	public record Hooks(LongSupplier clock, BiConsumer<Player, String> notifier, Function<Runnable, Runnable> scheduler,
	                    Consumer<String> debug) {

		/**
		 * Wall clock, an action bar that outlasts one task period, a {@link RepeatingTimer} every {@link #PERIOD_TICKS}
		 * on the main thread (it reads and moves entities) and the module's debug log.
		 */
		public static Hooks live(JavaPlugin plugin) {
			return new Hooks(System::currentTimeMillis,
			                 (player, text) -> ActionBarManager.send(plugin, player, text, PERIOD_TICKS + 20L), task -> {
				RepeatingTimer timer = new RepeatingTimer(plugin, PERIOD_TICKS, t -> task.run());
				timer.start(false);
				return timer::stop;
			}, line -> log.debug(line));
		}
	}

	/** One pending cop report: who did what where, who saw it, who radios it in and when it lands. */
	private static final class Case {

		private final Player         killer;
		private final String         crimeId;
		private final Location       at;
		private final @Nullable UUID victimId;
		private final List<CopNpc>   witnesses;
		private final long           landsAt;
		private       CopNpc         reporter;

		private Case(Player killer, String crimeId, Location at, @Nullable UUID victimId, List<CopNpc> witnesses,
		             long landsAt) {
			this.killer    = killer;
			this.crimeId   = crimeId;
			this.at        = at;
			this.victimId  = victimId;
			this.witnesses = witnesses;
			this.landsAt   = landsAt;
			this.reporter  = witnesses.get(0);
		}
	}

	/** A report waiting for its criminal's next join. */
	private record Deferred(String crimeId, Location at, int witnesses, ReportKind kind, String reporter, long until) {
	}

	private final ChaseConfigLoader config;
	private final CopLoader         copLoader;
	private final CopManager        copManager;
	private final PatrolManager     patrol;
	private final CopPerception     perception;
	private final WitnessService    civilians;
	private final HeatLedger        ledger;
	private final CrimeService      crimes;
	private final CopRadio          copRadio;
	private final WantedMessages    messages;
	private final DetainmentService detainment;
	private final Hooks             hooks;

	private final List<Case>                  cases    = new ArrayList<>();
	private final Map<UUID, List<Deferred>>   deferred = new HashMap<>();
	private @Nullable Runnable                stopTimer;

	public CopCrimeReports(ChaseConfigLoader config, CopLoader copLoader, CopManager copManager, PatrolManager patrol,
	                       CopPerception perception, WitnessService civilians, HeatLedger ledger, CrimeService crimes,
	                       CopRadio copRadio, WantedMessages messages, DetainmentService detainment, Hooks hooks) {
		this.config     = config;
		this.copLoader  = copLoader;
		this.copManager = copManager;
		this.patrol     = patrol;
		this.perception = perception;
		this.civilians  = civilians;
		this.ledger     = ledger;
		this.crimes     = crimes;
		this.copRadio   = copRadio;
		this.messages   = messages;
		this.detainment = detainment;
		this.hooks      = hooks;
	}

	/**
	 * A gated crime nobody reported yet (the witness gate held it): a cop eyewitness opens a report; else a civilian
	 * who saw it calls 911; else nobody saw it. One reporter per crime: a cop report suppresses the civilian call.
	 * <p>
	 * One cop is one crime: a Kill_Cop of a cop whose Assault_Cop report is pending replaces that report and keeps its
	 * deadline when a cop saw the kill. No cop saw the kill but another living cop saw the assault: the assault report
	 * stands and the civilians are never asked (a cop report beats a call; killing the caller must not forgive what a
	 * cop saw). Only the victim saw the assault: it closes silently and the civilians report the kill, if anyone does.
	 */
	public void open(Player killer, String crimeId, @Nullable Location at, @Nullable UUID victimId) {
		Location     spot    = at != null ? at : killer.getLocation();
		List<CopNpc> eyes    = eyes(killer, crimeId, spot, victimId);
		Case         assault = Crimes.KILL_COP.equals(crimeId) && victimId != null ? assaultOn(killer, victimId) : null;
		if (eyes.isEmpty()) {
			if (assault != null && seenByAnother(assault, victimId)) {
				log("stand", assault, " reason=unseen_kill");
				return;
			}
			int callers = civilians.witness(killer, crimeId, spot);
			hooks.debug().accept("WITNESS " + killer.getName() + " crime=" + crimeId + " callers=" + callers);
			if (assault != null) supersede(assault);
			if (callers == 0) notify(killer, WantedMessages.Key.WITNESS_NO_WITNESS, Map.of());
			return;
		}

		long landsAt = now() + cops().reportSeconds() * 1000L;
		if (assault != null) {
			supersede(assault);
			landsAt = assault.landsAt;
		}
		Case report = new Case(killer, crimeId, spot, victimId, eyes, landsAt);
		cases.add(report);
		glow(report.reporter, true);
		log("open", report, "");
		countdown(report);
	}

	/**
	 * The cops who saw {@code killer} commit {@code crimeId} at {@code at}, nearest to the spot first (PLAN.md 1.2): a
	 * valid squad or beat cop, not the dead cop of a Kill_Cop, who sees him (within Alert_Range as the light, crowds and
	 * sneaking shrink it, or within Witness_Floor with a clear line of sight; a disguise hides nothing here) and who is
	 * fighting or chasing him, or hears a loud crime (turning to it), or has him inside his View_Angle cone.
	 */
	List<CopNpc> eyes(Player killer, String crimeId, Location at, @Nullable UUID victimId) {
		WitnessSettings.Cops rules    = cops();
		double               range    = alertRange();
		boolean              loud     = rules.loudCrimes().contains(crimeId) || civilians.loudWithin(killer);
		boolean              kill     = Crimes.KILL_COP.equals(crimeId);
		Location             killerAt = killer.getLocation();

		Set<CopNpc> candidates = new LinkedHashSet<>(copManager.liveCops());
		candidates.addAll(patrol.beatCops());
		List<CopNpc> eyes = new ArrayList<>();
		for (CopNpc cop : candidates) {
			LivingEntity body = cop.isValid() ? cop.getEntity() : null;
			if (body == null || kill && body.getUniqueId().equals(victimId)) continue;
			if (!perception.sees(cop, killer, range) && !cop.canSee(killer, rules.witnessFloor())) continue;

			boolean engaged = killer.getUniqueId().equals(cop.getTargetPlayerId()) &&
			                  (cop.getCurrentState() == CopState.COMBAT || cop.getCurrentState() == CopState.PURSUING);
			if (!engaged && !loud && !WitnessService.inView(body.getLocation(), killerAt, rules.viewAngle())) continue;
			if (loud && !engaged) face(cop, at);
			eyes.add(cop);
		}
		eyes.sort(Comparator.comparingDouble(cop -> distanceSquared(cop, at)));
		return eyes;
	}

	/** One run of the task: hand-overs, cut calls, countdowns and landings, then forgets expired deferred reports. */
	public void tick() {
		long now = now();
		for (Case report : List.copyOf(cases)) {
			if (!report.killer.isOnline()) {
				deferCase(report);
				continue;
			}

			report.witnesses.removeIf(cop -> !cop.isValid() || cop.getEntity() == null);
			if (report.witnesses.isEmpty()) {
				close(report);
				log("cut", report, "");
				notify(report.killer, WantedMessages.Key.WITNESS_REPORT_CUT, Map.of());
				continue;
			}
			if (!report.witnesses.contains(report.reporter)) {
				glow(report.reporter, false);
				report.reporter = report.witnesses.get(0);
				glow(report.reporter, true);
			}
			if (now < report.landsAt) {
				countdown(report);
				continue;
			}

			close(report);
			String refused = land(report.killer, ReportKind.COP, report.crimeId, report.at, report.witnesses.size(),
			                      copRadio.memberName(report.reporter));
			log(refused == null ? "land" : "drop", report, refused == null ? "" : " reason=" + refused);
		}

		for (List<Deferred> waiting : deferred.values()) waiting.removeIf(report -> now > report.until());
		deferred.values().removeIf(List::isEmpty);
	}

	/** The criminal's fate settled him ({@code reason}: death, cuffed, arrested): his pending reports are dropped. */
	public void drop(Player killer, String reason) {
		for (Case report : casesOf(killer)) {
			close(report);
			log("drop", report, " reason=" + reason);
		}
		deferred.remove(killer.getUniqueId());
	}

	/** The criminal logged out: every pending report with a witness alive waits for his next join (OD-5). */
	public void onQuit(Player killer) {
		for (Case report : casesOf(killer)) deferCase(report);
	}

	/**
	 * A report that could not land because its criminal is away ({@code witnesses} saw it): it lands at his next join
	 * within Defer_Minutes. A 911 call cut short by his logout comes here (T-551); nothing is committed now.
	 */
	public void defer(Player criminal, String crimeId, Location at, int witnesses, ReportKind kind) {
		deferred.computeIfAbsent(criminal.getUniqueId(), id -> new ArrayList<>())
		        .add(new Deferred(crimeId, at.clone(), Math.max(1, witnesses), kind, "",
		                          now() + config.getWitnesses().deferMinutes() * 60_000L));
		log("defer", criminal, crimeId, kind.name(), witnesses, at, "");
	}

	/**
	 * A civilian's 911 call landed live (PLAN.md 1.5): the crime is committed as a CIVILIAN report with the call's
	 * {@code viewers}, through the same landing as a cop's (restrained = dropped, a live sighting kept, Caller_911 about
	 * the crime spot). {@code ponytail:} S5 closes the dead cop's silence here too.
	 */
	public void landCivilian(Player criminal, String crimeId, Location at, int viewers) {
		String refused = land(criminal, ReportKind.CIVILIAN, crimeId, at, viewers, "");
		log(refused == null ? "land" : "drop", criminal, crimeId, ReportKind.CIVILIAN.name(), viewers, at,
		    refused == null ? "" : " reason=" + refused);
	}

	/** His user data is loaded (one tick after it): every report deferred within Defer_Minutes lands now. */
	public void onJoin(Player killer) {
		List<Deferred> waiting = deferred.remove(killer.getUniqueId());
		if (waiting == null) return;

		long    now    = now();
		boolean landed = false;
		for (Deferred report : waiting) {
			if (now > report.until()) continue;

			String refused = land(killer, report.kind(), report.crimeId(), report.at(), report.witnesses(),
			                      report.reporter());
			log(refused == null ? "land" : "drop", killer, report.crimeId(),
			    report.reporter().isEmpty() ? report.kind().name() : plain(report.reporter()), report.witnesses(),
			    report.at(), refused == null ? " deferred=true" : " reason=" + refused);
			landed |= refused == null;
		}
		if (landed) notify(killer, WantedMessages.Key.WITNESS_DEFERRED, Map.of());
	}

	/** Pending (not deferred) reports about {@code killerId}; for tests. */
	int pending(UUID killerId) {
		return (int) cases.stream().filter(report -> report.killer.getUniqueId().equals(killerId)).count();
	}

	/** Starts (or restarts) the 10-tick task. */
	public void start() {
		stop();
		stopTimer = hooks.scheduler().apply(this::tick);
	}

	public void stop() {
		Runnable stopper = stopTimer;
		stopTimer = null;
		if (stopper != null) stopper.run();
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		start();
	}

	/** A reload stops the task; the reports are kept and {@link #onInitialize} restarts it. */
	@Override
	public void onPreClear() {
		stop();
	}

	@Override
	public void onShutdown() {
		stop();
		for (Case report : List.copyOf(cases)) close(report);
		deferred.clear();
	}

	// ── internals ───────────────────────────────────────────────────────────

	/**
	 * Commits the reported crime ({@code kind}, {@code witnesses}; seen by a cop only for a cop's report), then tips
	 * the squad to the crime spot (unless a cop sees him now: a live sighting beats a late tip) and has dispatch say it
	 * there: Officer_Report for a cop's, Caller_911 for a call. {@code null} when it landed, else why nothing was
	 * committed: {@code restrained} (cuffed, in transit, jailed: the arrest settled him, at the deadline and at a join
	 * alike) or {@code refused} (the recommit was cancelled).
	 */
	private @Nullable String land(Player killer, ReportKind kind, String crimeId, Location at, int witnesses,
	                              String reporter) {
		if (detainment.isRestrained(killer)) return "restrained";

		boolean cop = kind == ReportKind.COP;
		boolean committed = ledger.commitReported(killer, kind,
		                                          () -> crimes.commit(killer, crimeId, at, cop, witnesses));
		if (!committed) return "refused";

		// the commit can create the group (its star trigger runs the wanted start), so it is looked up after.
		// ponytail: no group (heat under the first star) = no radio line, as the 0.21.1 911 path; S5's group-less
		// dispatchAt (PLAN.md 2.4) can speak here too
		CopGroup group = copManager.groupOf(killer.getUniqueId());
		if (group == null) return null;

		if (!group.getSquad().hasFreshSighting()) group.getSquad().reportTip(at);
		Map<String, String> extras = new HashMap<>();
		extras.put("crime", messages.crimeName(crimeId));
		if (cop) extras.put("reporter", reporter);
		copRadio.dispatchAt(group, at, killer, cop ? "Officer_Report" : "Caller_911", 0, "", extras);
		return null;
	}

	/** A report whose criminal left: deferred when a witness is still alive, else gone with him. */
	private void deferCase(Case report) {
		close(report);
		report.witnesses.removeIf(cop -> !cop.isValid() || cop.getEntity() == null);
		if (report.witnesses.isEmpty()) {
			log("cut", report, " reason=quit");
			return;
		}
		if (!report.witnesses.contains(report.reporter)) report.reporter = report.witnesses.get(0);

		deferred.computeIfAbsent(report.killer.getUniqueId(), id -> new ArrayList<>())
		        .add(new Deferred(report.crimeId, report.at, report.witnesses.size(), ReportKind.COP,
		                          copRadio.memberName(report.reporter),
		                          now() + config.getWitnesses().deferMinutes() * 60_000L));
		log("defer", report, "");
	}

	/** A Kill_Cop replaces the victim's assault case: closed with no Report_Cut, one cop is one crime. */
	private void supersede(Case assault) {
		close(assault);
		log("drop", assault, " reason=superseded");
	}

	/** Whether a living cop other than the victim saw the assault. */
	private static boolean seenByAnother(Case assault, UUID victimId) {
		for (CopNpc cop : assault.witnesses) {
			LivingEntity body = cop.isValid() ? cop.getEntity() : null;
			if (body != null && !body.getUniqueId().equals(victimId)) return true;
		}
		return false;
	}

	private @Nullable Case assaultOn(Player killer, UUID victimId) {
		for (Case report : casesOf(killer)) {
			if (Crimes.ASSAULT_COP.equals(report.crimeId) && victimId.equals(report.victimId)) return report;
		}
		return null;
	}

	private List<Case> casesOf(Player killer) {
		UUID id = killer.getUniqueId();
		return cases.stream().filter(report -> report.killer.getUniqueId().equals(id)).toList();
	}

	/** Removes the report; its reporter stops glowing unless he still reports another one. */
	private void close(Case report) {
		cases.remove(report);
		if (cases.stream().noneMatch(other -> other.reporter == report.reporter)) glow(report.reporter, false);
	}

	private void countdown(Case report) {
		long left = Math.max(0L, report.landsAt - now());
		notify(report.killer, WantedMessages.Key.WITNESS_COP_CALLING,
		       Map.of("reporter", name(report.reporter), "seconds", String.valueOf((left + 999L) / 1000L)));
	}

	private void glow(CopNpc cop, boolean on) {
		LivingEntity body = cop.getEntity();
		if (body == null || on && !cops().reporterGlow()) return;
		body.setGlowing(on);
	}

	private static void face(CopNpc cop, Location at) {
		NPC npc = cop.getNpc();
		if (npc != null) npc.faceLocation(at);
	}

	/** {@code Witness.No_Witness} and the rest: {@code ""} turns a line off. */
	private void notify(Player killer, WantedMessages.Key key, Map<String, String> values) {
		String text = messages.format(key, values);
		if (text != null && !text.isBlank()) hooks.notifier().accept(killer, text);
	}

	private void log(String verb, Case report, String suffix) {
		log(verb, report.killer, report.crimeId, name(report.reporter), report.witnesses.size(), report.at, suffix);
	}

	/** {@code REPORT open|land|cut|drop|defer|stand <player> crime=<id> reporter=<name> witnesses=<n> at=x,y,z}. */
	private void log(String verb, Player killer, String crimeId, String reporter, int witnesses, Location at,
	                 String suffix) {
		hooks.debug().accept("REPORT " + verb + " " + killer.getName() + " crime=" + crimeId + " reporter=" + reporter +
		                     " witnesses=" + witnesses + " at=" + coords(at) + suffix);
	}

	/** The cop as the radio names him, without colours. */
	private String name(CopNpc cop) {
		return plain(copRadio.memberName(cop));
	}

	private static String plain(@Nullable String text) {
		return text == null ? "" : ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text)).trim();
	}

	private static String coords(Location at) {
		return at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ();
	}

	private static double distanceSquared(CopNpc cop, Location at) {
		LivingEntity body = cop.getEntity();
		if (body == null || at.getWorld() == null) return Double.MAX_VALUE;

		Location from = body.getLocation();
		return at.getWorld().equals(from.getWorld()) ? from.distanceSquared(at) : Double.MAX_VALUE;
	}

	private double alertRange() {
		CopConfigProvider provider = copLoader.getLoadedProvider();
		return provider == null ? DEFAULT_ALERT_RANGE : provider.getAlertRange();
	}

	private WitnessSettings.Cops cops() {
		return config.getWitnesses().cops();
	}

	private long now() {
		return hooks.clock().getAsLong();
	}
}
