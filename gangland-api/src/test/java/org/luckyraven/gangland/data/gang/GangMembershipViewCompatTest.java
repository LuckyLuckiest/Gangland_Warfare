package org.luckyraven.gangland.data.gang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * api 2.6 is additive: a view written against 2.0 (three abstract methods) still compiles and answers the new
 * members with their documented defaults, and the holder answers absent defaults when no view is installed.
 */
class GangMembershipViewCompatTest {

	private final UUID a = UUID.randomUUID();
	private final UUID b = UUID.randomUUID();
	private final UUID c = UUID.randomUUID();

	// implements ONLY the three abstract methods
	private final GangMembershipView oldStyle = new GangMembershipView() {
		@Override
		public int gangIdOf(UUID uuid) {
			if (uuid.equals(a) || uuid.equals(b)) return 1;
			if (uuid.equals(c)) return 2;
			return -1;
		}

		@Override
		public boolean gangsAllied(int gangIdA, int gangIdB) {
			return (gangIdA == 1 && gangIdB == 3) || (gangIdA == 3 && gangIdB == 1);
		}

		@Override
		public Optional<String> nameOf(int gangId) {
			return Optional.empty();
		}
	};

	@Test
	@DisplayName("oldStyleView_stillLoadsAndAnswersDefaults")
	void oldStyleView_stillLoadsAndAnswersDefaults() {
		GangMembership holder = new GangMembership();
		holder.install(oldStyle);

		assertTrue(oldStyle.pvpBlocked(a, b, null), "same gang");
		assertFalse(oldStyle.pvpBlocked(a, c, null), "different, not allied");
		assertEquals(holder.alliedOrSame(a, b), oldStyle.pvpBlocked(a, b, null));
		assertEquals(holder.alliedOrSame(a, c), oldStyle.pvpBlocked(a, c, null));
		assertFalse(oldStyle.pvpBlocked(UUID.randomUUID(), UUID.randomUUID(), null), "two gangless players");

		assertTrue(oldStyle.gangIds().isEmpty());
		assertEquals(-1, oldStyle.gangIdByName("x"));
		assertTrue(oldStyle.membersOf(1).isEmpty());
		assertTrue(oldStyle.ownerOf(1).isEmpty());
		assertEquals(GangChatChannel.PUBLIC, oldStyle.chatChannelOf(a));
	}

	@Test
	@DisplayName("holderWithoutView_returnsAbsentDefaults")
	void holderWithoutView_returnsAbsentDefaults() {
		GangMembership holder = new GangMembership();

		assertFalse(holder.pvpBlocked(a, b, null));
		assertTrue(holder.gangIds().isEmpty());
		assertEquals(-1, holder.gangIdByName("x"));
		assertTrue(holder.membersOf(1).isEmpty());
		assertTrue(holder.ownerOf(1).isEmpty());
		assertEquals(GangChatChannel.PUBLIC, holder.chatChannelOf(a));
	}
}
