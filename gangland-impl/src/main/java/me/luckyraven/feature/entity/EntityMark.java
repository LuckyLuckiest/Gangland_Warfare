package me.luckyraven.feature.entity;

public enum EntityMark {

	CIVILIAN,
	POLICE,
	UNSET;

	public boolean isCivilian() {
		return this == CIVILIAN || this == POLICE;
	}

	public boolean countForWanted() {
		return this == CIVILIAN || this == POLICE;
	}

}
