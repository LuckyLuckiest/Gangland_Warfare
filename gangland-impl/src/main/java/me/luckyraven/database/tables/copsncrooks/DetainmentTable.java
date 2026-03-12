package me.luckyraven.database.tables.copsncrooks;

import me.luckyraven.copsncrooks.detainment.DetainedPlayer;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class DetainmentTable extends Table<DetainedPlayer> {

	public DetainmentTable(JailTable jailTable) {
		super("detainment");

		Attribute<String>  playerUuid = new Attribute<>("player_uuid", true, String.class);
		Attribute<Integer> jailId     = new Attribute<>("jail_id", false, Integer.class);
		Attribute<String>  state      = new Attribute<>("state", false, String.class);

		jailId.setUnique(true);
		jailId.setForeignKey(jailTable.get("id"), jailTable);

		this.addAttribute(playerUuid);
		this.addAttribute(jailId);
		this.addAttribute(state);
	}

	@Override
	public Object[] getData(DetainedPlayer data) {
		return new Object[]{data.getPlayerId().toString(), data.getState().name()};
	}

	@Override
	public Map<String, Object> searchCriteria(DetainedPlayer data) {
		return createSearchCriteria("player_uuid = ?", new Object[]{data.getPlayerId().toString()},
									new int[]{Types.VARCHAR}, new int[]{0});
	}
}
