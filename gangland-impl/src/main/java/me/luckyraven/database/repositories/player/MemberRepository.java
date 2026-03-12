package me.luckyraven.database.repositories.player;

import lombok.Setter;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.database.tables.player.MemberTable;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.database.tables.rank.RankTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
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

@Setter
@Repository(Member.class)
public class MemberRepository extends AbstractRepository<Member> {

	private final MemberTable memberTable;

	private RankManager rankManager;
	private GangManager gangManager;

	public MemberRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		UserTable userTable = new UserTable();
		RankTable rankTable = new RankTable();
		this.memberTable = new MemberTable(userTable, rankTable);
	}

	@Override
	protected Collection<Member> doLoadAll() throws SQLException {
		List<Member>   members = new ArrayList<>();
		List<Object[]> data    = getDatabase().table(memberTable.getName()).selectAll();

		for (Object[] result : data) {
			int v = 0;

			UUID   uuid         = UUID.fromString(String.valueOf(result[v++]));
			int    gangId       = (int) result[v++];
			double contribution = (double) result[v++];
			int    rankId       = (int) result[v++];
			long   joinedGang   = (long) result[v];

			Rank   rank   = rankManager.get(rankId);
			Member member = new Member(uuid);

			if (rank == null) {
				// convert the rank to the initial rank (head)
				rank = rankManager.getRankTree().getRoot().getData();
			}

			member.setGangId(gangId);
			member.setContribution(contribution);
			member.setRank(rank);
			member.setGangJoinDateLong(joinedGang);

			members.add(member);

			Gang gang = gangManager.getGang(gangId);
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
