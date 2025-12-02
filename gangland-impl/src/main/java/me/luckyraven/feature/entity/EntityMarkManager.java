package me.luckyraven.feature.entity;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EntityMarkManager {

	private final JavaPlugin            plugin;
	private final NamespacedKey         entityMarkKey;
	private final Map<UUID, EntityMark> entityMarks;

	public EntityMarkManager(JavaPlugin plugin) {
		this.plugin        = plugin;
		this.entityMarkKey = new NamespacedKey(plugin, "entity_mark");
		this.entityMarks   = new HashMap<>();
	}

	public void setEntityMark(Entity entity, EntityMark mark) {
		entityMarks.put(entity.getUniqueId(), mark);

		// store in persistent data for survival across server restarts
		PersistentDataContainer dataContainer = entity.getPersistentDataContainer();

		dataContainer.set(entityMarkKey, PersistentDataType.STRING, mark.name());
	}

	public EntityMark getEntityMark(Entity entity) {
		// check cache first
		if (entityMarks.containsKey(entity.getUniqueId())) {
			return entityMarks.get(entity.getUniqueId());
		}

		// check persistent data
		var    dataContainer = entity.getPersistentDataContainer();
		String markStr       = dataContainer.get(entityMarkKey, PersistentDataType.STRING);

		if (markStr != null) {
			try {
				EntityMark mark = EntityMark.valueOf(markStr);

				entityMarks.put(entity.getUniqueId(), mark);

				return mark;
			} catch (IllegalArgumentException ignored) { }
		}

		// default based on entity type
		return getDefaultMarkForType(entity.getType());
	}

	public boolean isCivilian(Entity entity) {
		return getEntityMark(entity).isCivilian();
	}

	public boolean countsForWanted(Entity entity) {
		return getEntityMark(entity).countForWanted();
	}

	public void removeEntityMark(Entity entity) {
		entityMarks.remove(entity.getUniqueId());

		PersistentDataContainer dataContainer = entity.getPersistentDataContainer();

		dataContainer.remove(entityMarkKey);
	}

	public void clearCache() {
		entityMarks.clear();
	}

	protected EntityMark getDefaultMarkForType(EntityType type) {
		return switch (type) {
			case VILLAGER, WANDERING_TRADER, PLAYER -> EntityMark.CIVILIAN;
			case ZOMBIE, SKELETON, CREEPER, SPIDER, WITHER_SKELETON, ENDERMAN -> EntityMark.NEUTRAL;
			case PILLAGER -> EntityMark.POLICE;
			case VINDICATOR, EVOKER -> EntityMark.CRIMINAL;
			default -> EntityMark.UNSET;
		};
	}
}
