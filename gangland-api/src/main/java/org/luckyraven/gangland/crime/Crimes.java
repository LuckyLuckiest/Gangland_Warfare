package org.luckyraven.gangland.crime;

/**
 * Crime ids for the 0.15 publishers. Ids are plain strings so a new crime needs no api bump; these constants match the
 * shipped heat weight table.
 */
public final class Crimes {

	public static final String KILL_PLAYER      = "Kill_Player";
	public static final String KILL_CIVILIAN    = "Kill_Civilian";
	public static final String KILL_COP         = "Kill_Cop";
	public static final String ASSAULT_COP      = "Assault_Cop";
	public static final String ASSAULT_CIVILIAN = "Assault_Civilian";
	public static final String RESISTING_ARREST = "Resisting_Arrest";

	private Crimes() {
	}

}
