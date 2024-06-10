package me.luckyraven.data.account.gang;

import me.luckyraven.Gangland;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.MemberTable;
import me.luckyraven.file.configuration.SettingAddon;

import java.sql.Types;
import java.util.*;

public class MemberManager {

	private final Gangland          gangland;
	private final Map<UUID, Member> members;

	public MemberManager(Gangland gangland) {
		this.gangland = gangland;
		this.members  = new HashMap<>();
	}

	public void initialize(MemberTable memberTable, GangManager gangManager, RankManager rankManager) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			List<Object[]> rowsData = database.table(memberTable.getName()).selectAll();

			for (Object[] result : rowsData) {
				int    v            = 0;
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

				members.put(uuid, member);

				Gang gang = gangManager.getGang(gangId);
				if (gang != null) gang.addMember(member);
			}
		});
	}

	public void initializeMemberData(Member member, MemberTable memberTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			Database config = database.table(memberTable.getName());

			Object[] memberInfo = config.select("uuid = ?", new Object[]{member.getUuid()}, new int[]{Types.CHAR},
												new String[]{"*"});

			// create member data into a database
			if (memberInfo.length == 0) {
				if (!SettingAddon.isAutoSave()) memberTable.insertTableQuery(database, member);
			} else {
				RankManager rankManager = gangland.getInitializer().getRankManager();

				int    v            = 1;
				int    gangId       = (int) memberInfo[v++];
				double contribution = (double) memberInfo[v++];
				int    rankId       = (int) memberInfo[v++];
				long   gangJoin     = (long) memberInfo[v];

				Rank rank = rankManager.get(rankId);

				if (rank == null) {
					// convert the rank to the initial rank (head)
					rank = rankManager.getRankTree().getRoot().getData();
				}

				member.setGangId(gangId);
				member.setContribution(contribution);
				member.setRank(rank);
				member.setGangJoinDateLong(gangJoin);
			}
		});
	}

	public void add(Member member) {
		members.put(member.getUuid(), member);
	}

	public boolean remove(Member member) {
		Member m = members.remove(member.getUuid());
		return m != null;
	}

	public void clear() {
		members.clear();
	}

	public boolean contains(Member member) {
		return members.containsKey(member.getUuid());
	}

	public Member getMember(UUID uuid) {
		return members.get(uuid);
	}

	public int size() {
		return members.size();
	}

	public Map<UUID, Member> getMembers() {
		return Collections.unmodifiableMap(members);
	}

}
