package me.luckyraven.database.tables;

import me.luckyraven.copsncrooks.detainment.jail.Jail;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.util.Pair;

import java.sql.Types;
import java.util.Map;
import java.util.UUID;

public class JailedPlayersTable extends Table<Pair<Jail, UUID>> {

	public JailedPlayersTable(JailTable jailTable) {
		super("jailed_players");

		Attribute<Integer> id   = new Attribute<>("id", true, Integer.class);
		Attribute<UUID>    uuid = new Attribute<>("uuid", false, UUID.class);

		uuid.setUnique(true);

		id.setForeignKey(jailTable.get("id"), jailTable);

		this.addAttribute(id);
		this.addAttribute(uuid);
	}

	@Override
	public Object[] getData(Pair<Jail, UUID> data) {
		return new Object[]{data.first().getId(), data.second().toString()};
	}

	@Override
	public Map<String, Object> searchCriteria(Pair<Jail, UUID> data) {
		return createSearchCriteria("id = ? AND uuid = ?",
									new Object[]{data.first().getId(), data.second().toString()},
									new int[]{Types.INTEGER, Types.VARCHAR}, new int[]{0, 1});
	}
}
