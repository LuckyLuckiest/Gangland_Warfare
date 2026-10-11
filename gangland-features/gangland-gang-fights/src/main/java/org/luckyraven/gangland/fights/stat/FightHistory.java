package org.luckyraven.gangland.fights.stat;

import org.luckyraven.gangland.fights.database.FightPlayerStats;
import org.luckyraven.gangland.fights.database.FightRecord;
import org.luckyraven.gangland.fights.database.FightStats;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.Participant;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The fight tables in memory (autosave writes them back through the repositories): per-gang and per-player results and
 * the record of every finished fight, which the bet caps and the ranking read.
 * ponytail: the record list grows without bound and every query scans it; index by day or prune when a server
 * keeps tens of thousands of fights.
 */
public class FightHistory implements BeanLifecycle {

	public static final long DAY_MS = 86_400_000L;

	private final IRepository<FightStats>       statsRepository;
	private final IRepository<FightPlayerStats> playerRepository;
	private final IRepository<FightRecord>      recordRepository;

	private final Map<Integer, FightStats>    gangs   = new ConcurrentHashMap<>();
	private final Map<UUID, FightPlayerStats> players = new ConcurrentHashMap<>();
	private final List<FightRecord>           records = new CopyOnWriteArrayList<>();

	public FightHistory(IRepository<FightStats> statsRepository, IRepository<FightPlayerStats> playerRepository,
	                    IRepository<FightRecord> recordRepository) {
		this.statsRepository  = statsRepository;
		this.playerRepository = playerRepository;
		this.recordRepository = recordRepository;

		statsRepository.setDataSupplier(gangs::values);
		playerRepository.setDataSupplier(players::values);
		recordRepository.setDataSupplier(() -> records);
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (!firstLoad) return;

		statsRepository.loadAll().forEach(row -> gangs.put(row.gangId(), row));
		playerRepository.loadAll().forEach(row -> players.put(row.uuid(), row));
		records.addAll(recordRepository.loadAll());
	}

	@Override
	public void onClear() {
		// unsaved results must survive /glw reload
	}

	/** Records a finished fight: the record row, both gangs' results and every fighter's. Returns the row. */
	public FightRecord record(Fight fight, int winnerGangId, long endedAt) {
		long id = records.stream().mapToLong(FightRecord::id).max().orElse(0L) + 1;
		FightRecord row = new FightRecord(id, fight.gangA(), fight.gangB(), winnerGangId, fight.bet(), fight.ranked(),
		                                  endedAt);
		records.add(row);

		boolean draw = winnerGangId < 0;
		for (int gang : new int[]{fight.gangA(), fight.gangB()}) {
			FightStats current = stats(gang);
			gangs.put(gang, draw ? current.withDraw() : gang == winnerGangId ? current.withWin() : current.withLoss());
		}
		if (!draw) {
			for (Participant participant : fight.participants()) {
				FightPlayerStats current = playerStats(participant.player());
				players.put(participant.player(),
				            participant.gangId() == winnerGangId ? current.withWin() : current.withLoss());
			}
		}
		return row;
	}

	/** Adds kills to a fighter (the fight flow counts them while it runs). */
	public void addKills(UUID player, int kills) {
		players.put(player, playerStats(player).withKills(kills));
	}

	public FightStats stats(int gangId) {
		return gangs.getOrDefault(gangId, new FightStats(gangId, 0, 0, 0));
	}

	public FightPlayerStats playerStats(UUID player) {
		return players.getOrDefault(player, new FightPlayerStats(player, 0, 0, 0));
	}

	public List<FightRecord> records() {
		return List.copyOf(records);
	}

	/** How much one of the two gangs took from the other today, net, as an absolute value. */
	public double pairNetToday(int gangA, int gangB, long now) {
		double net = 0;
		for (FightRecord row : today(now)) {
			if (row.draw() || !between(row, gangA, gangB)) continue;
			net += row.winner() == gangA ? row.bet() : -row.bet();
		}
		return Math.abs(net);
	}

	/** What a gang has won from bets today. */
	public double wonToday(int gangId, long now) {
		double total = 0;
		for (FightRecord row : today(now)) {
			if (row.winner() == gangId) total += row.bet();
		}
		return total;
	}

	public int rankedBetweenToday(int gangA, int gangB, long now) {
		int count = 0;
		for (FightRecord row : today(now)) {
			if (row.ranked() && between(row, gangA, gangB)) count++;
		}
		return count;
	}

	/** Milliseconds since the last ranked fight between the two, or Long.MAX_VALUE when there was none. */
	public long millisSinceLastRanked(int gangA, int gangB, long now) {
		long last = -1;
		for (FightRecord row : records) {
			if (row.ranked() && between(row, gangA, gangB)) last = Math.max(last, row.endedAt());
		}
		return last < 0 ? Long.MAX_VALUE : now - last;
	}

	/** Ranked wins of {@code gangId}, at most {@code perOpponentPerDay} counted per opponent per day. */
	public long boardWins(int gangId, int perOpponentPerDay) {
		Map<String, Integer> perOpponentDay = new HashMap<>();
		long                 total          = 0;
		for (FightRecord row : records) {
			if (!row.ranked() || row.winner() != gangId) continue;

			int seen = perOpponentDay.merge(row.loser() + ":" + row.endedAt() / DAY_MS, 1, Integer::sum);
			if (seen <= perOpponentPerDay) total++;
		}
		return total;
	}

	private List<FightRecord> today(long now) {
		long day = now / DAY_MS;
		return records.stream().filter(row -> row.endedAt() / DAY_MS == day).toList();
	}

	private static boolean between(FightRecord row, int gangA, int gangB) {
		return row.gangA() == gangA && row.gangB() == gangB || row.gangA() == gangB && row.gangB() == gangA;
	}
}
