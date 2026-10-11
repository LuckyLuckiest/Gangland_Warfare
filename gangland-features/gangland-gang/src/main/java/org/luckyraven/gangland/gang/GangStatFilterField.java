package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.menu.filter.FilterField;

/** Gang sort axes that come from the stats snapshot (GP-39); the gang search menu can sort by them. */
public enum GangStatFilterField implements FilterField {

	KILLS("kills"),
	KDR("kdr"),
	ASSISTS("assists"),
	LEVEL("level"),
	ONLINE("online");

	private final String stat;

	GangStatFilterField(String stat) {
		this.stat = stat;
	}

	/** The ranking stat this field sorts by. */
	public String stat() {
		return stat;
	}

	@Override
	public String id() {
		return name();
	}

	@Override
	public InputKind inputKind() {
		return InputKind.RANGE;
	}
}
