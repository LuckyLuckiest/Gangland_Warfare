package org.luckyraven.gangland.database.tables.copsncrooks;

import org.luckyraven.gangland.copsncrooks.detainment.inventory.SeizedInventory;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class SeizedInventoryTable extends Table<SeizedInventory> {

	public SeizedInventoryTable() {
		super("seized_inventory");

		Attribute<String> playerUuid         = new Attribute<>("player_uuid", true, String.class);
		Attribute<String> serializedContents = new Attribute<>("serialized_contents", false, String.class);
		Attribute<Long>   seizedAt           = new Attribute<>("seized_at", false, Long.class);

		this.addAttribute(playerUuid);
		this.addAttribute(serializedContents);
		this.addAttribute(seizedAt);
	}

	@Override
	public Object[] getData(SeizedInventory data) {
		return new Object[]{data.getPlayerId().toString(), data.getSerializedContents(), data.getSeizedAt()};
	}

	@Override
	public Map<String, Object> searchCriteria(SeizedInventory data) {
		return createSearchCriteria("player_uuid = ?", new Object[]{data.getPlayerId().toString()},
		                            new int[]{Types.VARCHAR}, new int[]{0});
	}
}
