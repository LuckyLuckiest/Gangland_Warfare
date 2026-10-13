package org.luckyraven.gangland.crime;

/**
 * Crime ids for the 0.15 publishers and (2.7) the 0.20.0 street crimes. Ids are plain strings so a new crime needs no api bump; these constants match the
 * shipped heat weight table.
 */
public final class Crimes {

	public static final String KILL_PLAYER      = "Kill_Player";
	public static final String KILL_CIVILIAN    = "Kill_Civilian";
	public static final String KILL_COP         = "Kill_Cop";
	public static final String ASSAULT_COP      = "Assault_Cop";
	public static final String ASSAULT_CIVILIAN = "Assault_Civilian";
	public static final String RESISTING_ARREST = "Resisting_Arrest";

	public static final String BRANDISH_NEAR_COP = "Brandish_Near_Cop";
	public static final String CAR_THEFT = "Car_Theft";
	public static final String SAFE_CRACKING = "Safe_Cracking";
	public static final String STORE_ROBBERY = "Store_Robbery";
	public static final String TRESPASS_RESTRICTED = "Trespass_Restricted";
	public static final String EVADING_POLICE = "Evading_Police";

	private Crimes() {
	}

}
