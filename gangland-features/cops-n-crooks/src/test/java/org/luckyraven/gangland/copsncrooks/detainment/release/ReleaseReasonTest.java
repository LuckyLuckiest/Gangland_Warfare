package org.luckyraven.gangland.copsncrooks.detainment.release;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.21.0 fnd-origin: {@code RESCUED} is appended after the existing reasons (append-only merge rule with 0.20.0). */
@DisplayName("ReleaseReason - RESCUED")
class ReleaseReasonTest {

	@Test
	@DisplayName("RESCUED exists and every 0.19 reason keeps its ordinal")
	void rescued_isAppended() {
		assertNotNull(ReleaseReason.valueOf("RESCUED"));
		assertEquals(0, ReleaseReason.HANDCUFF_BRIBE.ordinal());
		assertEquals(5, ReleaseReason.ADMIN.ordinal());
		assertTrue(ReleaseReason.valueOf("RESCUED").ordinal() > ReleaseReason.ADMIN.ordinal(), "appended after ADMIN");
	}
}
