package me.luckyraven.database.tables;

import me.luckyraven.data.account.gang.Member;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
import java.util.Map;
import java.util.UUID;

public class MemberTable extends Table<Member> {

	public MemberTable(UserTable userTable, RankTable rankTable) {
		super("member");

		Attribute<UUID>    uuid         = new Attribute<>("uuid", true, UUID.class);
		Attribute<Integer> gangId       = new Attribute<>("gang_id", false, Integer.class);
		Attribute<Double>  contribution = new Attribute<>("contribution", false, Double.class);
		Attribute<Integer> rankId       = new Attribute<>("rank_id", false, Integer.class);
		Attribute<Long>    joinDate     = new Attribute<>("join_date", false, Long.class);

		gangId.setDefaultValue(-1);
		contribution.setDefaultValue(0D);
		joinDate.setDefaultValue(-1L);
		rankId.setDefaultValue(-1);

		uuid.setForeignKey(userTable.get("uuid"), userTable);
		rankId.setForeignKey(rankTable.get("id"), rankTable);

		this.addAttribute(uuid);
		this.addAttribute(gangId);
		this.addAttribute(contribution);
		this.addAttribute(rankId);
		this.addAttribute(joinDate);
	}

	@Override
	public Object[] getData(Member data) {
		return new Object[]{data.getUuid().toString(), data.getGangId(), data.getContribution(),
							data.getRank() == null ? -1 : data.getRank().getUsedId(), data.getGangJoinDateLong()};
	}

	@Override
	public Map<String, Object> searchCriteria(Member data) {
		return createSearchCriteria("uuid = ?", new Object[]{data.getUuid().toString()}, new int[]{Types.CHAR},
									new int[]{0});
	}
}
