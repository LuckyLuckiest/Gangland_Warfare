package org.luckyraven.gangland.copsncrooks.detainment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.support.FakeRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.21.0 fnd-origin (P-ORIGIN-1): the registry writes the arrest origin in the SAME save that creates or moves the row.
 * (DetainmentServiceOriginTest covers the service on top of this registry.)
 */
@DisplayName("DetainmentRegistry - the arrest origin")
class DetainmentRegistryOriginTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

	private FakeRepository<DetainedPlayer> repository;
	private DetainmentRegistry             registry;

	@BeforeEach
	void setUp() {
		repository = new FakeRepository<>();
		registry   = new DetainmentRegistry(repository, new JailRegistry());
	}

	@Test
	@DisplayName("a new HANDCUFFED row gets the origin in exactly one repository save")
	void handcuffWithOrigin_savesOnce() {
		registry.setState(ID, DetainmentState.HANDCUFFED, ArrestOrigin.SURRENDERED);

		assertEquals(1, repository.saved.size(), "one save, the row already carries the origin");
		assertEquals(ArrestOrigin.SURRENDERED, repository.saved.get(0).getOrigin());
		assertEquals(DetainmentState.HANDCUFFED, registry.getState(ID));
		assertSame(repository.saved.get(0), registry.getDetainedPlayers().get(ID));
	}

	@Test
	@DisplayName("the old two-argument overload leaves an existing origin alone and writes none on a new row")
	void twoArgSetState_leavesOriginAlone() {
		registry.setState(ID, DetainmentState.HANDCUFFED, ArrestOrigin.BUSTED_DOWNED);
		registry.setState(ID, DetainmentState.JAILED);

		assertEquals(ArrestOrigin.BUSTED_DOWNED, registry.getDetainedPlayers().get(ID).getOrigin());

		UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
		registry.setState(other, DetainmentState.HANDCUFFED);
		assertNull(registry.getDetainedPlayers().get(other).getOrigin(), "the legacy overload writes no origin");
	}

	@Test
	@DisplayName("a null origin on an existing row keeps what is there; a non-null origin replaces it; each call saves")
	void existingRow_originOnlyChangesWhenNonNull() {
		registry.setState(ID, DetainmentState.HANDCUFFED, ArrestOrigin.SURRENDERED);
		int saves = repository.saved.size();

		registry.setState(ID, DetainmentState.JAILED, null);
		assertEquals(ArrestOrigin.SURRENDERED, registry.getDetainedPlayers().get(ID).getOrigin());

		registry.setState(ID, DetainmentState.JAILED, ArrestOrigin.BUSTED_DOWNED);
		assertEquals(ArrestOrigin.BUSTED_DOWNED, registry.getDetainedPlayers().get(ID).getOrigin());
		assertTrue(repository.saved.size() >= saves + 2, "each call saves");
	}

	@Test
	@DisplayName("NORMAL with an origin still removes and deletes the row")
	void normalWithOrigin_removesTheRow() {
		registry.setState(ID, DetainmentState.HANDCUFFED, ArrestOrigin.SURRENDERED);

		registry.setState(ID, DetainmentState.NORMAL, ArrestOrigin.SURRENDERED);

		assertEquals(DetainmentState.NORMAL, registry.getState(ID));
		assertEquals(1, repository.deleted.size());
	}
}
