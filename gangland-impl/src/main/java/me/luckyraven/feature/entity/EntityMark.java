package me.luckyraven.feature.entity;

public enum EntityMark {

	CIVILIAN,       // regular civilian NPCs that contribute to the wanted level
	CRIMINAL,       // Criminal NPCs
	POLICE,         // Police/law enforcement NPCs
	GANG_MEMBER,    // Gang members NPCs
	NEUTRAL,        // Neutral entities
	UNSET;          // Not yet classified

	public boolean isCivilian() {
		return this == CIVILIAN || this == POLICE;
	}

	public boolean countForWanted() {
		return this == CIVILIAN || this == POLICE;
	}

}
