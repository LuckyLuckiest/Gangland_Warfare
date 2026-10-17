package org.luckyraven.gangland.crime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrimesTest {

	@Test
	@DisplayName("the six 0.20 ids equal the Crew_Heat / Heat weight keys")
	void newIdsMatchHeatWeightKeys() {
		assertEquals("Brandish_Near_Cop", Crimes.BRANDISH_NEAR_COP);
		assertEquals("Car_Theft", Crimes.CAR_THEFT);
		assertEquals("Safe_Cracking", Crimes.SAFE_CRACKING);
		assertEquals("Store_Robbery", Crimes.STORE_ROBBERY);
		assertEquals("Trespass_Restricted", Crimes.TRESPASS_RESTRICTED);
		assertEquals("Evading_Police", Crimes.EVADING_POLICE);
	}

	@Test
	@DisplayName("the six 0.15 ids are unchanged")
	void shippedIdsUnchanged() {
		assertEquals("Kill_Player", Crimes.KILL_PLAYER);
		assertEquals("Kill_Civilian", Crimes.KILL_CIVILIAN);
		assertEquals("Kill_Cop", Crimes.KILL_COP);
		assertEquals("Assault_Cop", Crimes.ASSAULT_COP);
		assertEquals("Assault_Civilian", Crimes.ASSAULT_CIVILIAN);
		assertEquals("Resisting_Arrest", Crimes.RESISTING_ARREST);
	}
}
