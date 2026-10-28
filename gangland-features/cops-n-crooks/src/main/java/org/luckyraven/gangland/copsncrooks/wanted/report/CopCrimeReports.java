package org.luckyraven.gangland.copsncrooks.wanted.report;

import lombok.CustomLog;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.events.npc.CopDeathEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CheckInSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.HandoffSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.SpawnBias;
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
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.timer.RepeatingTimer;
import org.luckyraven.keystone.util.ActionBarManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
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
 * <p>
 * Silent officers (0.21.2 S5, PLAN.md section 2): every cop checks in on the radio (every run while his squad is in
 * contact, else every {@code Check_In_Seconds}). A cop killed where no cop saw the killer or the body says nothing: his
 * slot stays filled ({@link #silentRoles}, {@link #silentStation}); dispatch says {@code Radio_Check} at his last
 * check-in + {@code Check_In_Seconds} and {@code Officer_Missing} at + {@code Silent_Seconds}, then records the
 * casualty, sends backup toward that spot (a beat cop's station sends two units) and opens a SCENE there, or, when
 * his group was hunting the killer, counts the kill ({@code Pursuit_Presumed}). A cop who sees the killer near a scene
 * counts the kill; a scene nobody spots him at goes cold. A 911 call about the kill closes the silence first. One
 * kill counts once: a call about a kill Officer_Missing presumed or a scene counted commits nothing (S5 review 1).
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
	                    Consumer<String> debug, Consumer<Event> events) {

		/** No event sink: the S4 reports fire none. */
		public Hooks(LongSupplier clock, BiConsumer<Player, String> notifier, Function<Runnable, Runnable> scheduler,
		             Consumer<String> debug) {
			this(clock, notifier, scheduler, debug, event -> {
			});
		}

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
			}, line -> log.debug(line), event -> plugin.getServer().getPluginManager().callEvent(event));
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

	/**
	 * A cop killed where nobody saw it (PLAN.md 2.2): his radio name, group and station as they were at his death, his
	 * last check-in, and whether his group was hunting the killer.
	 */
	private static final class Silence {

		private final CopNpc             cop;
		private final String             unit;
		private final @Nullable CopGroup group;
		private final OptionalInt        station;
		private final Player             killer;
		private final Location           lastReport;
		private final long               lastReportAt;
		private final @Nullable CopRole  role;
		private final boolean            hunting;
		private final Location           diedAt;
		private       boolean            radioChecked;

		private Silence(CopNpc cop, String unit, @Nullable CopGroup group, OptionalInt station, Player killer,
		                Location lastReport, long lastReportAt, Location diedAt) {
			this.cop          = cop;
			this.unit         = unit;
			this.group        = group;
			this.station      = station;
			this.killer       = killer;
			this.lastReport   = lastReport;
			this.lastReportAt = lastReportAt;
			this.role         = cop.getRole();
			this.hunting      = group != null && killer.getUniqueId().equals(group.getTargetPlayerId());
			this.diedAt       = diedAt;
		}
	}

	/**
	 * The scene of a cop kill nobody saw, at {@code at} (the body, or his last check-in), of the kill at {@code diedAt}:
	 * a cop who sees the killer near it counts the kill, until {@code until}.
	 */
	private static final class Scene {

		private final Player   killer;
		private final Location at;
		private final Location diedAt;
		private final long     until;
		private       boolean  found;

		private Scene(Player killer, Location at, Location diedAt, long until) {
			this.killer = killer;
			this.at     = at;
			this.diedAt = diedAt;
			this.until  = until;
		}
	}

	/** A cop kill Officer_Missing or a scene already committed: a 911 call about it commits nothing. */
	private record Counted(UUID killerId, Location diedAt, long since) {
	}

	/** Blocks between a 911 call's crime spot and a kill's body within which the call is about that kill. */
	private static final double KILL_SPOT_BLOCKS = 1.0;

	/**
	 * How long a counted kill waits for a 911 call about it.
	 * {@code ponytail:} a fixed bound, longer than any call with its hand-overs; a call id on CivilianCalledInEvent
	 * would make it exact.
	 */
	private static final long COUNTED_MILLIS = 5 * 60_000L;

	/** Blocks from a scene within which the first unit there says {@code Scene_Found}. */
	private static final double SCENE_FOUND_BLOCKS = 6.0;

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
	private final List<Silence>               silences = new ArrayList<>();
	private final List<Scene>                 scenes   = new ArrayList<>();
	private final List<Counted>               counted  = new ArrayList<>();
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

		List<CopNpc> eyes = new ArrayList<>();
		for (CopNpc cop : candidates()) {
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

		CheckInSettings rules = checkIn();
		if (rules.enabled()) stampCheckIns(now, rules);
		for (Silence silence : List.copyOf(silences)) tickSilence(silence, now, rules);
		for (Scene scene : List.copyOf(scenes)) tickScene(scene, now, rules);
		counted.removeIf(kill -> now - kill.since() > COUNTED_MILLIS);
	}

	/**
	 * {@code cop} was killed by {@code killer} (PLAN.md 2.2), before his squads hear of it: {@code true} when the death
	 * is silent - Check_In is on, Kill_Cop is held by the witness gate (with the heat ledger on), no cop report about
	 * him is open (a cop eyewitness of the kill, or of the assault on him, which stands: the death booked Kill_Cop just
	 * before, and that cop is on the radio) and no cop saw the body fall.
	 * The silence is recorded; the caller takes the cop out of his squads without a radio call. A body a cop saw fall
	 * opens a scene there and is no silence: the squad radios as before.
	 */
	public boolean silentPath(CopNpc cop, LivingEntity body, Player killer) {
		if (!checkIn().enabled() || !killCopGated() || copReportOn(killer, body.getUniqueId())) return false;

		Location diedAt = body.getLocation();
		if (bodySeen(killer, body)) {
			openScene(killer, diedAt, diedAt);
			return false;
		}

		long     now    = now();
		Location last   = cop.getLastReport();
		long     lastAt = cop.getLastReportAt();
		if (last == null || lastAt <= 0) {
			// killed before his first check-in (within 10 ticks of his spawn): he checked in from where he came from
			Location spawn = cop.getSpawnLocation();
			last   = spawn != null ? spawn : diedAt;
			lastAt = now;
		}
		Silence silence = new Silence(cop, copRadio.memberName(cop), cop.getGroup(), patrol.stationOf(cop), killer,
		                              last.clone(), lastAt, diedAt.clone());
		silences.add(silence);
		logCheckIn("silent", silence, now, "");
		return true;
	}

	/**
	 * The roles {@code group} still holds for its cops killed unseen: their slots stay filled until dispatch notices
	 * (Officer_Missing, a 911 call), so no refill appears for a death nobody knows of (T-545). Never counted as cops
	 * by a release or an episode trim.
	 */
	public List<CopRole> silentRoles(CopGroup group) {
		return silences.stream().filter(silence -> silence.group == group).map(silence -> silence.role).toList();
	}

	/** Whether a beat cop of station {@code stationId} is a silence dispatch has not noticed: no replacement yet. */
	public boolean silentStation(int stationId) {
		return silences.stream().anyMatch(
				silence -> silence.station.isPresent() && silence.station.getAsInt() == stationId);
	}

	/** Open scenes about {@code killerId}; for tests. */
	int scenes(UUID killerId) {
		return (int) scenes.stream().filter(scene -> scene.killer.getUniqueId().equals(killerId)).count();
	}

	/** The criminal's fate settled him ({@code reason}: death, cuffed, arrested): his pending reports are dropped. */
	public void drop(Player killer, String reason) {
		for (Case report : casesOf(killer)) {
			close(report);
			log("drop", report, " reason=" + reason);
		}
		deferred.remove(killer.getUniqueId());
		forget(killer, reason);
	}

	/** The criminal logged out: every pending report with a witness alive waits for his next join (OD-5). */
	public void onQuit(Player killer) {
		for (Case report : casesOf(killer)) deferCase(report);
		forget(killer, "quit");
	}

	/**
	 * A report that could not land because its criminal is away ({@code witnesses} saw it): it lands at his next join
	 * within Defer_Minutes. A 911 call cut short by his logout comes here (T-551); nothing is committed now.
	 */
	public void defer(Player criminal, String crimeId, Location at, int witnesses, ReportKind kind) {
		if (Crimes.KILL_COP.equals(crimeId) && consumeCounted(criminal, at)) {
			log("drop", criminal, crimeId, kind.name(), witnesses, at, " reason=counted");
			return;
		}
		deferred.computeIfAbsent(criminal.getUniqueId(), id -> new ArrayList<>())
		        .add(new Deferred(crimeId, at.clone(), Math.max(1, witnesses), kind, "",
		                          now() + config.getWitnesses().deferMinutes() * 60_000L));
		log("defer", criminal, crimeId, kind.name(), witnesses, at, "");
	}

	/**
	 * A civilian's 911 call landed live (PLAN.md 1.5): the crime is committed as a CIVILIAN report with the call's
	 * {@code viewers}, through the same landing as a cop's (restrained = dropped, a live sighting kept, Caller_911 about
	 * the crime spot). A call about a Kill_Cop closes the silence (else the scene) of the kill it reports, the one whose
	 * body lay at the call's crime spot (0.21.2 S5): dispatch learns of the death from the call, so its casualty, backup
	 * and response go out now, toward the call's spot, with no Officer_Missing line and no second commit. A call about a
	 * kill Officer_Missing presumed or a scene counted already commits nothing ({@code REPORT drop ... reason=counted}).
	 */
	public void landCivilian(Player criminal, String crimeId, Location at, int viewers) {
		boolean kill    = Crimes.KILL_COP.equals(crimeId);
		UUID    id      = criminal.getUniqueId();
		Silence silence = kill ? killAt(silences, id, at, s -> s.killer.getUniqueId(), s -> s.diedAt) : null;
		Scene   scene   = kill && silence == null ? killAt(scenes, id, at, s -> s.killer.getUniqueId(), s -> s.diedAt)
		                                          : null;
		if (kill && silence == null && scene == null && consumeCounted(criminal, at)) {
			log("drop", criminal, crimeId, ReportKind.CIVILIAN.name(), viewers, at, " reason=counted");
			return;
		}

		String refused = land(criminal, ReportKind.CIVILIAN, crimeId, at, viewers, "");
		log(refused == null ? "land" : "drop", criminal, crimeId, ReportKind.CIVILIAN.name(), viewers, at,
		    refused == null ? "" : " reason=" + refused);
		if (silence != null) {
			silences.remove(silence);
			logCheckIn("close", silence, now(), " reason=911");
			discover(silence, at);
		} else if (scene != null) {
			scenes.remove(scene);
			logScene("close", scene, " reason=911");
		}
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
		silences.clear();
		scenes.clear();
		counted.clear();
	}

	// ── internals ───────────────────────────────────────────────────────────

	/**
	 * Commits the reported crime ({@code kind}, {@code witnesses}; seen by a cop only for a cop's report), then tips
	 * the squad to the crime spot (unless a cop sees him now: a live sighting beats a late tip) and has dispatch say it
	 * there: Officer_Report for a cop's, Caller_911 for a call; a presumed kill (DISPATCH: Officer_Missing said it)
	 * and a scene sighting (SCENE, seen by a cop: the spotting cop speaks) say nothing here. {@code null} when it
	 * landed, else why nothing was
	 * committed: {@code restrained} (cuffed, in transit, jailed: the arrest settled him, at the deadline and at a join
	 * alike) or {@code refused} (the recommit was cancelled).
	 */
	private @Nullable String land(Player killer, ReportKind kind, String crimeId, Location at, int witnesses,
	                              String reporter) {
		if (detainment.isRestrained(killer)) return "restrained";

		boolean cop  = kind == ReportKind.COP;
		boolean seen = cop || kind == ReportKind.SCENE;
		boolean committed = ledger.commitReported(killer, kind,
		                                          () -> crimes.commit(killer, crimeId, at, seen, witnesses));
		if (!committed) return "refused";

		// the commit can create the group (its star trigger runs the wanted start), so it is looked up after. No group
		// (heat under the first star) = no radio line, as the 0.21.1 911 path
		CopGroup group = copManager.groupOf(killer.getUniqueId());
		if (group == null) return null;

		if (!group.getSquad().hasFreshSighting()) group.getSquad().reportTip(at);
		if (kind != ReportKind.COP && kind != ReportKind.CIVILIAN) return null;

		Map<String, String> extras = new HashMap<>();
		extras.put("crime", messages.crimeName(crimeId));
		if (cop) extras.put("reporter", reporter);
		copRadio.dispatchAt(group, at, killer, cop ? "Officer_Report" : "Caller_911", 0, "", extras);
		return null;
	}

	// ── silences and scenes (0.21.2 S5) ─────────────────────────────────────

	/** Every live squad and beat cop: the cops who can witness, check in and find a scene. */
	private Set<CopNpc> candidates() {
		Set<CopNpc> candidates = new LinkedHashSet<>(copManager.liveCops());
		candidates.addAll(patrol.beatCops());
		return candidates;
	}

	/** A cop checks in from where he stands: every run while his squad is in contact, else every Check_In_Seconds. */
	private void stampCheckIns(long now, CheckInSettings rules) {
		long every = rules.checkInSeconds() * 1000L;
		for (CopNpc cop : candidates()) {
			LivingEntity body = cop.isValid() ? cop.getEntity() : null;
			if (body == null) continue;

			NpcSquad squad   = cop.getCurrentSquad();
			boolean  contact = squad != null && squad.isInContact();
			if (contact || cop.getLastReportAt() <= 0 || now - cop.getLastReportAt() >= every)
				cop.stampReport(body.getLocation(), now);
		}
	}

	/** Radio_Check once at Check_In_Seconds past his last check-in, Officer_Missing at Silent_Seconds; a quit forgets it. */
	private void tickSilence(Silence silence, long now, CheckInSettings rules) {
		if (!silence.killer.isOnline()) {
			silences.remove(silence);
			logCheckIn("drop", silence, now, " reason=quit");
			return;
		}

		long age = now - silence.lastReportAt;
		if (age >= rules.silentSeconds() * 1000L) {
			missing(silence, now, rules);
			return;
		}
		if (!silence.radioChecked && age >= rules.checkInSeconds() * 1000L) {
			silence.radioChecked = true;
			say(null, silence.killer, silence.lastReport, "Radio_Check", Map.of("unit", silence.unit));
			logCheckIn("radio_check", silence, now, "");
		}
		if (rules.showTimer()) {
			long left = rules.silentSeconds() * 1000L - age;
			notify(silence.killer, WantedMessages.Key.WITNESS_SILENT,
			       Map.of("unit", plain(silence.unit), "seconds", String.valueOf((left + 999L) / 1000L)));
		}
	}

	/**
	 * Officer_Missing (PLAN.md 2.4): dispatch names his last check-in, learns of the death ({@link #discover}), then
	 * presumes the kill when his group was hunting the killer and still is his group now (x1.0, one witness, so it
	 * passes the witness gate), else (a group gone or replaced since counts as none) opens a scene there.
	 */
	private void missing(Silence silence, long now, CheckInSettings rules) {
		silences.remove(silence);
		logCheckIn("missing", silence, now, "");
		say(null, silence.killer, silence.lastReport, "Officer_Missing", Map.of("unit", silence.unit));
		boolean presumed = rules.pursuitPresumed() && silence.hunting &&
		                   copManager.groupOf(silence.group.getTargetPlayerId()) == silence.group;
		discover(silence, silence.lastReport);

		if (!presumed) {
			openScene(silence.killer, silence.lastReport, silence.diedAt);
			return;
		}
		String refused = land(silence.killer, ReportKind.DISPATCH, Crimes.KILL_COP, silence.lastReport, 1, "");
		log(refused == null ? "land" : "drop", silence.killer, Crimes.KILL_COP, ReportKind.DISPATCH.name(), 1,
		    silence.lastReport, refused == null ? "" : " reason=" + refused);
		if (refused == null) counted.add(new Counted(silence.killer.getUniqueId(), silence.diedAt, now));
	}

	/**
	 * The city learns of the death (Officer_Missing, a 911 call): the CopDeathEvent his silence held back fires; his
	 * group, still hunting the same suspect, records the casualty and requests backup (its cold-trail hold released when
	 * it hunts the killer, whose units then spawn on the side of {@code spot} with no hand-off lead past it, the squad
	 * tipped there, R4); a beat cop's station sends its held replacement and a beat mate to {@code spot}. A cop with
	 * neither (his group torn down since) sends nobody: the scene is all there is.
	 */
	private void discover(Silence silence, Location spot) {
		long now = copRadio.now();
		hooks.events().accept(new CopDeathEvent(silence.cop, silence.killer, false, silence.diedAt));

		CopGroup group = silence.group == null ? null : copManager.groupOf(silence.group.getTargetPlayerId());
		if (group != null && group == silence.group) {
			if (silence.hunting) group.setBackupHeld(false);
			group.recordCasualty(now);
			group.requestBackup(now, backupSettings());
			if (!silence.hunting) return;

			Location suspect = silence.killer.getLocation();
			if (suspect.getWorld() == spot.getWorld()) {
				HandoffSettings handoff = handoffSettings();
				group.setBias(new SpawnBias(spot.toVector().subtract(suspect.toVector()), spot,
				                            now + handoff.biasSeconds() * 1000L, handoff.coneDegrees()));
				group.spendBiasSeed();
			}
			if (!group.getSquad().hasFreshSighting()) group.getSquad().reportTip(spot);
			group.markTipOff(now);
		} else if (silence.station.isPresent()) {
			patrol.respondTo(silence.station.getAsInt(), spot);
		}
	}

	private void openScene(Player killer, Location at, Location diedAt) {
		Scene scene = new Scene(killer, at.clone(), diedAt.clone(), now() + checkIn().sceneSeconds() * 1000L);
		scenes.add(scene);
		logScene("open", scene, "");
		notify(killer, WantedMessages.Key.WITNESS_SCENE_HEADING, Map.of("place", copRadio.placeOf(at)));
	}

	/**
	 * A cop who sees the killer within Scene_Radius of the scene counts the kill (seen by a cop, a SCENE report) and
	 * says Scene_Suspect; the first unit within {@link #SCENE_FOUND_BLOCKS} says Scene_Found once; at Scene_Seconds
	 * dispatch says Scene_Clear and the trail goes cold, no stars. A killer who left is no longer looked for.
	 */
	private void tickScene(Scene scene, long now, CheckInSettings rules) {
		Player killer = scene.killer;
		if (!killer.isOnline()) {
			scenes.remove(scene);
			return;
		}

		Location     killerAt = killer.getLocation();
		double       radius   = rules.sceneRadius();
		boolean      near     = killerAt.getWorld() == scene.at.getWorld() &&
		                        killerAt.distanceSquared(scene.at) <= radius * radius;
		List<CopNpc> eyes     = near ? eyes(killer, Crimes.KILL_COP, killerAt, null) : List.of();
		if (!eyes.isEmpty()) {
			scenes.remove(scene);
			logScene("seen", scene, "");
			String refused = land(killer, ReportKind.SCENE, Crimes.KILL_COP, scene.at, eyes.size(), "");
			log(refused == null ? "land" : "drop", killer, Crimes.KILL_COP, ReportKind.SCENE.name(), eyes.size(),
			    scene.at, refused == null ? "" : " reason=" + refused);
			if (refused != null) return;

			counted.add(new Counted(killer.getUniqueId(), scene.diedAt, now));
			CopNpc   spotter = eyes.get(0);
			CopGroup group   = copManager.groupOf(killer.getUniqueId());
			// a cop has eyes on him: a sighting, not a tip
			if (group != null) group.getSquad().reportSighting(spotter, killerAt);
			say(spotter, killer, scene.at, "Scene_Suspect", Map.of());
			return;
		}
		if (now >= scene.until) {
			scenes.remove(scene);
			logScene("clear", scene, "");
			say(null, killer, scene.at, "Scene_Clear", Map.of());
			notify(killer, WantedMessages.Key.WITNESS_SCENE_COLD, Map.of());
			return;
		}
		if (scene.found) return;

		for (CopNpc cop : candidates()) {
			LivingEntity body  = cop.isValid() ? cop.getEntity() : null;
			Location     there = body == null ? null : body.getLocation();
			if (there == null || there.getWorld() != scene.at.getWorld()) continue;
			if (there.distanceSquared(scene.at) > SCENE_FOUND_BLOCKS * SCENE_FOUND_BLOCKS) continue;

			scene.found = true;
			logScene("found", scene, "");
			say(cop, killer, scene.at, "Scene_Found", Map.of());
			return;
		}
	}

	/** The killer died, was cuffed, arrested or logged out: his silences and scenes are forgotten. */
	private void forget(Player killer, String reason) {
		UUID id  = killer.getUniqueId();
		long now = now();
		for (Silence silence : List.copyOf(silences)) {
			if (!silence.killer.getUniqueId().equals(id)) continue;
			silences.remove(silence);
			logCheckIn("drop", silence, now, " reason=" + reason);
		}
		scenes.removeIf(scene -> scene.killer.getUniqueId().equals(id));
	}

	/**
	 * Dispatch ({@code speaker} null) or a cop says {@code key} about {@code place}, to the killer: on his group's
	 * radio, or with none (a beat cop's silence, an unwanted killer) on a throwaway group, which tips nobody.
	 */
	private void say(@Nullable CopNpc speaker, Player killer, Location place, String key, Map<String, String> extras) {
		CopGroup            group  = copManager.groupOf(killer.getUniqueId());
		CopGroup            voice  = group != null ? group : new CopGroup(killer.getUniqueId());
		Map<String, String> merged = new HashMap<>(extras);
		merged.putIfAbsent("place", copRadio.placeOf(place));
		if (speaker == null) copRadio.dispatchAt(voice, place, killer, key, voice.getLevel(), voice.getTierName(), merged);
		else copRadio.sayAs(voice, speaker, key, merged);
	}

	/**
	 * Whether a cop's report about cop {@code victimId} is open: a cop eyewitness saw his kill, or the assault on him
	 * (the report that stands when nobody saw the kill): that cop is on the radio, so the death is no silence.
	 */
	private boolean copReportOn(Player killer, UUID victimId) {
		return casesOf(killer).stream().anyMatch(report -> victimId.equals(report.victimId));
	}

	/** Forgets a counted kill of {@code killer} at {@code at}: {@code true} when the call is about one. */
	private boolean consumeCounted(Player killer, Location at) {
		Counted kill = killAt(counted, killer.getUniqueId(), at, Counted::killerId, Counted::diedAt);
		return kill != null && counted.remove(kill);
	}

	/** The kill of {@code killerId} whose body lay nearest {@code at}, within {@link #KILL_SPOT_BLOCKS}; else null. */
	private static <T> @Nullable T killAt(List<T> kills, UUID killerId, Location at, Function<T, UUID> killer,
	                                      Function<T, Location> spot) {
		T      nearest = null;
		double best    = KILL_SPOT_BLOCKS * KILL_SPOT_BLOCKS;
		for (T kill : kills) {
			Location there = spot.apply(kill);
			if (!killerId.equals(killer.apply(kill)) || there.getWorld() != at.getWorld()) continue;

			double distance = there.distanceSquared(at);
			if (distance > best) continue;
			nearest = kill;
			best    = distance;
		}
		return nearest;
	}

	/** A living cop saw {@code body} fall: he sees it (or it is within Witness_Floor) and it is loud or in his cone. */
	private boolean bodySeen(Player killer, LivingEntity body) {
		WitnessSettings.Cops rules = cops();
		double               range = alertRange();
		boolean              loud  = rules.loudCrimes().contains(Crimes.KILL_COP) || civilians.loudWithin(killer);
		Location             at    = body.getLocation();
		for (CopNpc cop : candidates()) {
			LivingEntity eye = cop.isValid() ? cop.getEntity() : null;
			if (eye == null || eye.getUniqueId().equals(body.getUniqueId())) continue;
			if (!perception.sees(cop, body, range) && !cop.canSee(body, rules.witnessFloor())) continue;
			if (!loud && !WitnessService.inView(eye.getLocation(), at, rules.viewAngle())) continue;

			if (loud) face(cop, at);
			return true;
		}
		return false;
	}

	/** The silent path needs Kill_Cop held by the witness gate, which the heat ledger books it through. */
	private boolean killCopGated() {
		WitnessSettings witnesses = config.getWitnesses();
		return witnesses.enabled() && witnesses.crimes().contains(Crimes.KILL_COP) && config.get().heat().enabled();
	}

	/** {@code CHECKIN silent|radio_check|missing|close|drop <unit> last=x,y,z age=<s>}. */
	private void logCheckIn(String verb, Silence silence, long now, String suffix) {
		hooks.debug().accept("CHECKIN " + verb + " " + plain(silence.unit) + " last=" + coords(silence.lastReport) +
		                     " age=" + (now - silence.lastReportAt) / 1000L + suffix);
	}

	/** {@code SCENE open|found|seen|clear|close <player> at=x,y,z}. */
	private void logScene(String verb, Scene scene, String suffix) {
		hooks.debug().accept("SCENE " + verb + " " + scene.killer.getName() + " at=" + coords(scene.at) + suffix);
	}

	private CheckInSettings checkIn() {
		CopConfigProvider provider = copLoader.getLoadedProvider();
		CheckInSettings   settings = provider == null ? null : provider.getCheckInSettings();
		return settings != null ? settings : CheckInSettings.DEFAULT;
	}

	private BackupSettings backupSettings() {
		CopConfigProvider provider = copLoader.getLoadedProvider();
		BackupSettings    settings = provider == null ? null : provider.getBackupSettings();
		return settings != null ? settings : BackupSettings.DEFAULT;
	}

	private HandoffSettings handoffSettings() {
		CopConfigProvider provider = copLoader.getLoadedProvider();
		HandoffSettings   settings = provider == null ? null : provider.getHandoffSettings();
		return settings != null ? settings : HandoffSettings.DEFAULT;
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
