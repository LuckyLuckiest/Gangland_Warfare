package me.luckyraven.database.component;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.util.utilities.DatabaseUtil;

import java.util.UUID;

@Getter
@Setter
public class Attribute<T> {

	@Getter(AccessLevel.NONE)
	private final Class<T> classType;

	private final String   name;
	private final int      type;
	private final int      size;
	private final boolean  primaryKey;
	private       Table<?> associatedTable;
	private       T        defaultValue;
	private       boolean  unique, canBeNull;

	@Setter(AccessLevel.NONE)
	private Attribute<?> foreignKey;

	public Attribute(String name, boolean primaryKey, Class<T> classType) {
		this(name, primaryKey, getLocalSize(classType), classType);
	}

	public Attribute(String name, boolean primaryKey, int size, Class<T> classType) {
		this(name, primaryKey, DatabaseUtil.getColumnType(classType), size, classType);
	}

	public Attribute(String name, int type, boolean primaryKey, Class<T> classType) {
		this(name, primaryKey, type, getLocalSize(classType), classType);
	}

	public Attribute(String name, boolean primaryKey, int type, int size, Class<T> classType) {
		this.name       = name.toLowerCase();
		this.type       = type;
		this.size       = size;
		this.primaryKey = primaryKey;
		this.classType  = classType;
	}

	private static <T> int getLocalSize(Class<T> classType) {
		if (classType.equals(UUID.class)) return 36;
		else if (classType.equals(String.class)) return 255;
		return 0;
	}

	public void setForeignKey(Attribute<?> attribute, Table<?> associatedTable) {
		this.foreignKey      = attribute;
		this.associatedTable = associatedTable;
	}

}
