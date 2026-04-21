package me.luckyraven.data.account.gang.member;

import me.luckyraven.Gangland;
import me.luckyraven.core.bean.BeanLifecycle;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.permission.vault.VaultPermissionBridge;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.repositories.player.MemberRepository;
import me.luckyraven.database.tables.player.MemberTable;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.repository.IRepository;
import org.jetbrains.annotations.Nullable;

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

	public void initialize() {
		IRepository<Member> memberRepository = database.getRepositoryRegistry().getRepository(Member.class);

		if (memberRepository instanceof MemberRepository concrete) {
			concrete.setRankManager(rankManager);
			concrete.setGangManager(gangManager);
		}

		for (Member member : memberRepository.loadAll()) {
			members.put(member.getUuid(), member);
		}

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
		initialize();
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

	/**
	 * Returns every cached member currently wearing the given rank. Used by rank-permission mutation sites to propagate
	 * changes through {@link VaultPermissionBridge}.
	 */
	public List<Member> getMembersByRank(Rank rank) {
		if (rank == null) return Collections.emptyList();

		List<Member> result = new ArrayList<>();

		for (Member member : members.values()) {
			if (member.getRank() == rank) result.add(member);
		}

		return result;
	}

	/**
	 * Sets a member's rank and mirrors the transition to Vault (revokes the old rank's nodes/group, grants the new
	 * rank's nodes/group). Callers must use this instead of {@link Member#setRank(Rank)} so external permission plugins
	 * stay in sync. When Vault is absent, this behaves exactly like the direct setter.
	 */
	public void assignRank(Member member, @Nullable Rank newRank) {
		Rank oldRank = member.getRank();
		member.setRank(newRank);

		VaultPermissionBridge.onRankTransition(member.getUuid(), oldRank, newRank);
	}

	/**
	 * Pushes a permission-node add/remove onto every online member currently wearing the rank. Invoked from the
	 * {@code /glw rank permission add|remove} command sites after {@code RankManager} has mutated its internal list.
	 */
	public void applyRankPermissionChange(Rank rank, String node, boolean added) {
		VaultPermissionBridge.applyPermissionChange(getMembersByRank(rank), node, added);
	}

}
