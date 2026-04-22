package me.luckyraven.database.repositories.player;

import lombok.CustomLog;
import me.luckyraven.database.tables.player.MemberTable;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.database.tables.rank.RankTable;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.gang.contract.MemberRepositoryContract;
import me.luckyraven.gang.contract.RankLookupContract;
import me.luckyraven.gang.member.Member;
import me.luckyraven.gang.rank.Rank;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.database.query.QueryBuilder;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@CustomLog
@Repository(Member.class)
public class MemberRepository extends AbstractRepository<Member> implements MemberRepositoryContract {

	private final MemberTable memberTable;

	private RankLookupContract rankLookup;
	private GangLookupContract gangLookup;

	public MemberRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		UserTable userTable = new UserTable();
		RankTable rankTable = new RankTable();
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
		List<Member>   members = new ArrayList<>();
		List<Object[]> data    = memberTable.selectAllTableQuery(getDatabase());

		for (Object[] result : data) {
			int v = 0;

			UUID   uuid         = UUID.fromString(String.valueOf(result[v++]));
			int    gangId       = (int) result[v++];
			double contribution = (double) result[v++];
			int    rankId       = (int) result[v++];
			long   joinedGang   = (long) result[v];

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

			Gang gang = gangLookup.findById(gangId);

			// Self-heal: stale gang_id points at a gang that no longer exists.
			// Reset the member's gang link in memory and persist -1 to the table immediately.
			if (gangId != -1 && gang == null) {
				log.warn("Member {} referenced deleted gang {}; clearing gang link.", uuid, gangId);
				member.setGangId(-1);
				member.setContribution(0D);
				member.setRank(null);

				QueryBuilder.on(getDatabase(), memberTable.getName())
				            .update()
				            .set("gang_id", -1)
				            .set("contribution", 0D)
				            .set("rank_id", -1)
				            .where("uuid", uuid.toString())
				            .execute();
			}

			members.add(member);

			if (gang != null) gang.addMember(member);
		}

		return members;
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
		Database table = getDatabase().table(memberTable.getName());
		table.delete("uuid", data.getUuid(), Types.VARCHAR);
	}
}
