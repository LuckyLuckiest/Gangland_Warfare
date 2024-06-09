package me.luckyraven.database.tables;

import me.luckyraven.data.account.gang.Member;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MemberTable extends Table<Member> {

	public MemberTable(UserTable userTable, GangTable gangTable, RankTable rankTable) {
		super("member");

		Attribute<UUID>    uuid         = new Attribute<>("uuid", true);
		Attribute<Integer> gangId       = new Attribute<>("gang_id", false);
		Attribute<Double>  contribution = new Attribute<>("contribution", false);
		Attribute<Integer> rankId       = new Attribute<>("rank_id", false);
		Attribute<Long>    joinDate     = new Attribute<>("join_date", false);

		gangId.setDefaultValue(-1);
		contribution.setDefaultValue(0D);
		joinDate.setDefaultValue(-1L);
		rankId.setDefaultValue(-1);

		uuid.setForeignKey(userTable.get("uuid"), userTable);
		gangId.setForeignKey(gangTable.get("id"), gangTable);
		rankId.setForeignKey(rankTable.get("id"), rankTable);

		this.addAttribute(uuid);
		this.addAttribute(gangId);
		this.addAttribute(contribution);
		this.addAttribute(joinDate);
		this.addAttribute(rankId);
	}

	@Override
	public Object[] getData(Member data) {
		return new Object[]{data.getUuid(), data.getGangId(), data.getContribution(),
							data.getRank() == null ? null : data.getRank().getUsedId(), data.getGangJoinDateLong()};
	}

	@Override
	public Map<String, Object> searchCriteria(Member data) {
		Map<String, Object> search = new HashMap<>();

		search.put("search", "uuid = ?");
		search.put("info", new Object[]{data.getUuid()});
		search.put("type", new int[]{Types.CHAR});
		search.put("index", new int[]{0});

		return Collections.unmodifiableMap(search);
	}
}
