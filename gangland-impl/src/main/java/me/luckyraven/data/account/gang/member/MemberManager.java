package me.luckyraven.data.account.gang.member;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.TableLookup;
import me.luckyraven.database.tables.player.MemberTable;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.autowire.bean.BeanLifecycle;

import java.util.*;

public class MemberManager implements BeanLifecycle {

	private final Gangland          gangland;
	private final GanglandDatabase  database;
	private final GangManager       gangManager;
	private final RankManager       rankManager;
	private final Map<UUID, Member> members;

	public MemberManager(Gangland gangland,
	                     GanglandDatabase database,
	                     GangManager gangManager,
	                     RankManager rankManager) {
		this.gangland    = gangland;
		this.database    = database;
		this.gangManager = gangManager;
		this.rankManager = rankManager;
		this.members     = new HashMap<>();
	}

	public void initialize(MemberTable memberTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, database);

		helper.runQueries(db -> {
			List<Object[]> rowsData = memberTable.selectAllTableQuery(db);

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

		// Set data supplier so repositoryRegistry.saveAll() can persist members
		IRepository<Member> memberRepository = database.getRepositoryRegistry().getRepository(Member.class);

		memberRepository.setDataSupplier(members::values);
	}

	public void initializeMemberData(Member member, MemberTable memberTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, database);

		helper.runQueries(db -> {
			Map<String, Object> search = memberTable.searchCriteria(member);
			Object[] memberInfo = db.table(memberTable.getName())
			                        .select((String) search.get("search"), (Object[]) search.get("info"),
			                                (int[]) search.get("type"), new String[]{"*"});

			// create member data into a database
			if (memberInfo.length == 0) {
				if (!Settings.isAutoSave()) memberTable.insertTableQuery(db, member);
			} else {
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

	@Override
	public void onClear() {
		clear();
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		MemberTable memberTable = TableLookup.find(MemberTable.class, database.getTables());
		initialize(memberTable);
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
