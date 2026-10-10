package org.luckyraven.gangland.gang.database.tables.gang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GangTableStateDefaultTest {

	@Test
	@DisplayName("GR-38: gang.state column defaults to INVITE, not OPEN")
	void stateDefault_isInvite() {
		assertEquals("INVITE", new GangTable().get("state").getDefaultValue());
	}

}
