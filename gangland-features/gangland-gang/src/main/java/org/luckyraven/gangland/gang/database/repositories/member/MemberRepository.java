package org.luckyraven.gangland.gang.database.repositories.member;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.database.fk.ForeignUserTable;
import org.luckyraven.gangland.gang.database.tables.member.MemberTable;
import org.luckyraven.gangland.gang.database.tables.rank.RankTable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.LoadOutcome;
import org.luckyraven.gangland.gang.contract.MemberRepositoryContract;
import org.luckyraven.gangland.gang.contract.RankLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import static org.luckyraven.gangland.gang.database.repositories.gang.GangRepository.num;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@CustomLog
@Repository(Member.class)
public class MemberRepository extends AbstractRepository<Member> implements MemberRepositoryContract, LoadOutcome {

	private final MemberTable memberTable;
	private volatile boolean   lastLoadFailed;

	private RankLookupContract rankLookup;
	private GangLookupContract gangLookup;

	public MemberRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		ForeignUserTable userTable = new ForeignUserTable();
		RankTable        rankTable = new RankTable();
		this.memberTable = new MemberTable(userTable, rankTable);
	}

	@Override
	public void setRankLookup(RankLookupContract rankLookup) {
		this.rankLookup = rankLookup;
	}

	@Override
	public void setGangLookup(GangLookupContract gangLookup) {
		this.gangLookup = gangLookup;
	}

	@Override
	protected Collection<Member> doLoadAll() throws SQLException {
		lastLoadFailed = true;   // stays true when readRows throws; Keystone reports the exception and returns an empty list
		Collection<Member> rows = readRows();
		lastLoadFailed = false;
		return rows;
	}

	@Override
	public boolean lastLoadFailed() {
		return lastLoadFailed;
	}

	private Collection<Member> readRows() throws SQLException {
		List<Member>   members = new ArrayList<>();
		List<Object[]> data    = tableBackend().selectAll();

		for (Object[] result : data) {
			Member member;
			try {
				member = fromRow(result, rankLookup);
			} catch (IllegalArgumentException exception) {
				// T-262: a NULL or malformed uuid skips this row instead of failing the whole member load
				log.warn("Skipping a member row with a bad uuid '{}'", result.length > 0 ? result[0] : null);
				continue;
			}
			UUID   uuid   = member.getUuid();
			int    gangId = member.getGangId();

			Gang gang = gangLookup.findById(gangId);

			// Self-heal: stale gang_id points at a gang that no longer exists.
			// Reset the member's gang link in memory and persist -1 to the table immediately.
			if (gangId != -1 && gang == null) {
				log.warn("Member {} referenced deleted gang {}; clearing gang link.", uuid, gangId);
				member.setGangId(-1);
				member.setContribution(0D);
				member.setRank(null);

				getBackend().execute("UPDATE " + memberTable.getName() +
				                     " SET gang_id = ?, contribution = ?, rank_id = ? WHERE uuid = ?",
				                     -1, 0D, -1, uuid.toString());
			}

			members.add(member);

			if (gang != null) gang.addMember(member);
		}

		return members;
	}

	/** One member row. Every numeric column reads through {@link Number}; a SQL NULL reads as 0 (T-199). */
	public static Member fromRow(Object[] result, RankLookupContract rankLookup) {
		int v = 0;

		UUID   uuid         = UUID.fromString(String.valueOf(result[v++]));
		int    gangId       = num(result[v++]).intValue();
		double contribution = num(result[v++]).doubleValue();
		int    rankId       = num(result[v++]).intValue();
		long   joinedGang   = num(result[v++]).longValue();
		// columns added in 0.18.0: an older or shorter row reads as 0
		int kills   = result.length > v ? num(result[v]).intValue() : 0;
		int deaths  = result.length > v + 1 ? num(result[v + 1]).intValue() : 0;
		int assists = result.length > v + 2 ? num(result[v + 2]).intValue() : 0;

		Rank   rank   = rankLookup.get(rankId);
		Member member = new Member(uuid);

		if (rank == null) {
			// convert the rank to the initial rank (head)
			rank = rankLookup.getRootRank();
		}

		member.setGangId(gangId);
		member.setContribution(contribution);
		member.setRank(rank);
		member.setGangJoinDateLong(joinedGang);
		member.setKills(kills);
		member.setDeaths(deaths);
		member.setAssists(assists);

		return member;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<Member> getTable() {
		return memberTable;
	}

	@Override
	protected void doDelete(Member data) throws SQLException {
		tableBackend().delete("uuid = ?", data.getUuid());
	}
}
