package org.luckyraven.gangland.gang.stats;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.util.NumberUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Gang kills, deaths, assists and the ranking snapshot. One immutable {@link Snapshot} is built on the main thread
 * every {@code Stats.Snapshot_Seconds} and shared by {@code /glw gang ranking}, {@code /glw gang profile}, the menus and
 * the placeholders, so all of them show the same numbers. The no-argument constructor builds an inert service (an
 * always-empty snapshot) for tests that only need the seam.
 */
public class GangStatsService {

	/** The always-available stats; a {@link GangStatContribution} adds its own key (turfs, wins, ...). */
	public static final List<String> BASE_STATS   = List.of("kills", "kdr", "assists", "level", "members", "online");
	/** Stats that show gang or member money; they need {@code gangland.gang.ranking.wealth}. */
	public static final List<String> WEALTH_STATS = List.of("balance", "money");
	/** Stats that exist even when no module contributes them (they read {@code 0}). */
	private static final List<String> MODULE_STATS = List.of("turfs", "wins");

	/** One gang at the moment of the snapshot. */
	public record Row(int gangId, String name, int level, int members, int online, long kills, long deaths,
	                  long assists, double kdr, double balance, double membersMoney, Map<String, Long> extra) {

		public Row {
			extra = Collections.unmodifiableMap(new LinkedHashMap<>(extra));
		}

		/** The value ranked for {@code stat}; {@code 0} for a stat this row does not know. */
		public double value(String stat) {
			return switch (stat) {
				case "kills" -> kills;
				case "deaths" -> deaths;
				case "kdr" -> kdr;
				case "assists" -> assists;
				case "level" -> level;
				case "members" -> members;
				case "online" -> online;
				case "balance" -> balance;
				case "money" -> membersMoney;
				default -> extra.getOrDefault(stat, 0L);
			};
		}
	}

	/** The ranking at one moment. Immutable; {@link #ranked} sorts a copy. */
	public record Snapshot(long takenAtMillis, List<Row> rows, Set<String> stats, int minKills) {

		public Snapshot {
			rows  = List.copyOf(rows);
			stats = Collections.unmodifiableSet(new LinkedHashSet<>(stats));
		}

		public Snapshot(long takenAtMillis) {
			this(takenAtMillis, List.of(), Set.of(), 0);
		}

		/** The row of {@code gangId}, if the gang existed at snapshot time. */
		public Optional<Row> row(int gangId) {
			return rows.stream().filter(row -> row.gangId() == gangId).findFirst();
		}

		/**
		 * The gangs ordered by {@code stat}, biggest first, equal values by gang id. The {@code kdr} ranking leaves out
		 * gangs below {@code Ranking.Min_Kills} kills. Empty for a stat that is not in {@link #stats}.
		 */
		public List<Row> ranked(String stat) {
			if (!stats.contains(stat)) return List.of();

			List<Row> sorted = new ArrayList<>(rows);
			if (stat.equals("kdr")) sorted.removeIf(row -> row.kills() < minKills);
			sorted.sort(Comparator.comparingDouble((Row row) -> row.value(stat))
					            .reversed()
					            .thenComparingInt(Row::gangId));
			return Collections.unmodifiableList(sorted);
		}
	}

	@Nullable
	private final JavaPlugin            plugin;
	@Nullable
	private final GangManager           gangManager;
	@Nullable
	private final GangRules             rules;
	@Nullable
	private volatile Supplier<GangStatContributions> contributions;
	@Nullable
	private final ToDoubleFunction<UUID> money;
	@Nullable
	private final IntFunction<Integer>  online;

	private volatile Snapshot snapshot = new Snapshot(0L);

	public GangStatsService() {
		this(null, null, null, null, null, null);
	}

	/**
	 * @param money  a member's wallet, looked up from the online then the offline user manager ({@code 0} when unknown)
	 * @param online how many members of a gang (by id) are online right now
	 */
	public GangStatsService(@Nullable JavaPlugin plugin, @Nullable GangManager gangManager, @Nullable GangRules rules,
	                        @Nullable Supplier<GangStatContributions> contributions, @Nullable ToDoubleFunction<UUID> money,
	                        @Nullable IntFunction<Integer> online) {
		this.plugin        = plugin;
		this.gangManager   = gangManager;
		this.rules         = rules;
		this.contributions = contributions;
		this.money         = money;
		this.online        = online;
	}

	/** Where the contributions of other modules are looked up on every refresh (the container is not a bean). */
	public void useContributions(Supplier<GangStatContributions> contributions) {
		this.contributions = contributions;
	}

	/** Starts the repeating main-thread refresh; does nothing without a server (tests). */
	@PostConstruct
	public void schedule() {
		if (plugin == null || gangManager == null || Bukkit.getServer() == null) return;

		long period = Math.max(1, rules == null ? 60 : rules.getStatsSnapshotSeconds()) * 20L;
		Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 20L, period);
	}

	/** Builds a new snapshot now. Main thread only. */
	public void refresh() {
		this.snapshot = build(System.currentTimeMillis());
	}

	/** The latest snapshot (shared, immutable). */
	public Snapshot snapshot() {
		return snapshot;
	}

	/** Every stat key {@code /glw gang ranking} accepts, in display order. */
	public List<String> statKeys() {
		return new ArrayList<>(snapshot.stats());
	}

	public boolean isWealth(String stat) {
		return WEALTH_STATS.contains(stat);
	}

	/**
	 * The ranking for {@code stat}; empty for an unknown stat, and for a wealth stat when the asker lacks the wealth
	 * node ({@code includeWealth} false).
	 */
	public List<Row> ranking(String stat, boolean includeWealth) {
		if (isWealth(stat) && !includeWealth) return List.of();
		return snapshot.ranked(stat);
	}

	/** Zeroes a member's kills, deaths and assists ({@code /glw option gang stats reset}). */
	public void resetPlayer(UUID player) {
		if (gangManager == null) return;

		for (Gang gang : gangManager.getAll()) {
			for (Member member : gang.getMembers()) {
				if (!member.getUuid().equals(player)) continue;
				member.setKills(0);
				member.setDeaths(0);
				member.setAssists(0);
			}
		}
	}

	Snapshot build(long now) {
		if (gangManager == null) return new Snapshot(now);

		Supplier<GangStatContributions> source = contributions;
		List<GangStatContribution> extras = source == null ? List.of() : source.get().all();
		Set<String>                stats  = new LinkedHashSet<>(BASE_STATS);
		stats.addAll(MODULE_STATS);
		for (GangStatContribution extra : extras) {
			stats.add(extra.key());
		}
		stats.addAll(WEALTH_STATS);

		List<Row> rows = new ArrayList<>();
		for (Gang gang : gangManager.getAll()) {
			long   kills = 0, deaths = 0, assists = 0;
			double wallets = 0;
			for (Member member : gang.getMembers()) {
				kills   += member.getKills();
				deaths  += member.getDeaths();
				assists += member.getAssists();
				if (money != null) wallets += money.applyAsDouble(member.getUuid());
			}

			Map<String, Long> extra = new LinkedHashMap<>();
			for (String key : MODULE_STATS) {
				extra.put(key, 0L);
			}
			for (GangStatContribution contribution : extras) {
				extra.put(contribution.key(), contribution.value(gang.getId()));
			}

			int    onlineNow = online == null ? 0 : online.apply(gang.getId());
			double kdr       = deaths == 0 ? kills : (double) kills / deaths;
			rows.add(new Row(gang.getId(), name(gang), gang.getLevel().getLevelValue(), gang.getMembers().size(),
			                 onlineNow, kills, deaths, assists, kdr, gang.getEconomy().getAmount().doubleValue(), wallets, extra));
		}

		return new Snapshot(now, rows, stats, rules == null ? 0 : rules.getRankingMinKills());
	}

	/** How a ranked value reads: kdr with two decimals, money grouped, every other stat a whole number. */
	public static String format(String stat, double value) {
		return switch (stat) {
			case "kdr" -> String.format(java.util.Locale.ROOT, "%.2f", value);
			case "balance", "money" -> NumberUtil.valueFormat(value);
			default -> String.valueOf((long) value);
		};
	}

	private static String name(Gang gang) {
		String display = gang.getDisplayNameString();
		return display == null ? String.valueOf(gang.getName()) : display;
	}
}
