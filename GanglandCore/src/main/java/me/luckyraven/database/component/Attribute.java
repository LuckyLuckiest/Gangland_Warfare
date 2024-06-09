package me.luckyraven.database.component;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.util.DatabaseUtil;

import java.util.UUID;

@Getter
@Setter
public class Attribute<T> {

	@Getter(AccessLevel.NONE) private final Class<T> classType;

	private final String   name;
	private final int      type;
	private final int      size;
	private final boolean  primaryKey;
	private       Table<?> associatedTable;
	private       T        defaultValue;
	private       boolean  unique, canBeNull;

	@Setter(AccessLevel.NONE) private Attribute<?> foreignKey;

	@SuppressWarnings("unchecked")
	public Attribute(String name, int type, int size, boolean primaryKey) {
		this.name       = name.toLowerCase();
		this.type       = type;
		this.size       = size;
		this.primaryKey = primaryKey;
		this.classType  = (Class<T>) Object.class;
	}

	public Attribute(String name, int type, boolean primaryKey) {
		this(name, type, 0, primaryKey);
	}

	@SuppressWarnings("unchecked")
	public Attribute(String name, boolean primaryKey) {
		this.name       = name;
		this.primaryKey = primaryKey;
		this.classType  = (Class<T>) Object.class;
		this.type       = DatabaseUtil.getColumnType(this.classType);
		this.size       = getLocalSize();
	}

	public void setForeignKey(Attribute<?> attribute, Table<?> associatedTable) {
		this.foreignKey      = attribute;
		this.associatedTable = associatedTable;
	}

	private int getLocalSize() {
		if (classType.equals(UUID.class)) return 36;
		return 0;
	}

}
