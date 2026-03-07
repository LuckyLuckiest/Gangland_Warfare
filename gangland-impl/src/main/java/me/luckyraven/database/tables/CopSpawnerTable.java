package me.luckyraven.database.tables;

import me.luckyraven.copsncrooks.police.spawn.CopSpawner;
import me.luckyraven.persistence.database.component.Table;

import java.util.Map;

public class CopSpawnerTable extends Table<CopSpawner> {

	public CopSpawnerTable() {
		super("cop_spawner");
	}

	@Override
	public Object[] getData(CopSpawner data) {
		return new Object[]{ };
	}

	@Override
	public Map<String, Object> searchCriteria(CopSpawner data) {
		return Map.of();
	}
}
