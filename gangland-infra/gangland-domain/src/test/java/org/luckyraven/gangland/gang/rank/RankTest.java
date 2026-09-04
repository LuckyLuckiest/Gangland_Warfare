package org.luckyraven.gangland.gang.rank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link Rank}'s permission-string matching, id identity, and the static {@code Rank.ID} counter contract
 * that {@link RankManager} relies on during {@code initialize()}/{@code clear()}.
 *
 * <p>In the same package as {@link Rank} so the package-protected {@code setID}/{@code addPermission}/
 * {@code removePermission} accessors are reachable without a public API change.
 */
@DisplayName("Rank - permission matching and id identity")
class RankTest {

	@Test
	void match_comparesAgainstUsedId() {
		Rank rank = new Rank("Boss", 7);

		assertTrue(rank.match(7));
		assertFalse(rank.match(8));
	}

	@Test
	@DisplayName("contains(String) is case-insensitive")
	void contains_isCaseInsensitive() {
		Rank rank = new Rank("Boss", 1, List.of(new Permission(1, "Gangland.Command.Gang.Force_Rank")));

		assertTrue(rank.contains("gangland.command.gang.force_rank"));
		assertTrue(rank.contains("GANGLAND.COMMAND.GANG.FORCE_RANK"));
		assertFalse(rank.contains("unrelated.node"));
	}

	@Test
	void getPermissions_isUnmodifiable() {
		Rank rank = new Rank("Boss", 1, List.of(new Permission(1, "node")));

		assertThrows(UnsupportedOperationException.class,
				() -> rank.getPermissions().add(new Permission(2, "other")));
	}

	@Test
	@DisplayName("addPermission/removePermission (package-visible, used by RankManager) mutate the backing list")
	void addAndRemovePermission_mutateBackingList() {
		Rank       rank       = new Rank("Boss", 1);
		Permission permission = new Permission(9, "some.node");

		rank.addPermission(permission);
		assertTrue(rank.contains(permission));

		rank.removePermission(permission);
		assertFalse(rank.contains(permission));
	}

	@Test
	@DisplayName("getNewId() increments a static counter shared across every Rank instance")
	void getNewId_incrementsSharedCounter() {
		Rank.setID(50);

		int first  = Rank.getNewId();
		int second = Rank.getNewId();

		assertEquals(50, first);
		assertEquals(51, second);
	}

}
