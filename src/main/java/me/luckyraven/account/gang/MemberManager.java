package me.luckyraven.account.gang;

import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.rank.Rank;
import me.luckyraven.rank.RankManager;

import java.util.*;

public class MemberManager {

	private final Map<UUID, Member> members;
	private final Gangland          gangland;

	public MemberManager(Gangland gangland) {
		this.gangland = gangland;
		this.members = new HashMap<>();
	}

	public void initialize(GangDatabase gangDatabase, GangManager gangManager, RankManager rankManager) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangDatabase);

		helper.runQueries(database -> {
			List<Object[]> rowsData = database.table("members").selectAll();

			for (Object[] result : rowsData) {
				UUID   uuid         = UUID.fromString(String.valueOf(result[0]));
				int    id           = (int) result[1];
				double contribution = (double) result[2];
				Rank   rank         = rankManager.get(String.valueOf(result[3]));
				long   joinedGang   = (long) result[4];


				Member member = new Member(uuid);
				member.setGangId(id);
				member.setContribution(contribution);
				member.setRank(rank);
				member.setGangJoinDateLong(joinedGang);

				members.put(uuid, member);

				Gang gang = gangManager.getGang(id);
				if (gang != null) gang.getGroup().add(member);
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
