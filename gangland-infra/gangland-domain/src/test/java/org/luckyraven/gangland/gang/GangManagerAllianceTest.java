package org.luckyraven.gangland.gang;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.contract.GangAllianceRepositoryContract;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.gang.support.RecordingGangAllianceRepository;
import org.luckyraven.keystone.persistence.repository.IRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Pins {@link GangManager#breakAlliance(Gang, Gang)} — the single seam every "the alliance is over" path must go
 * through.
 *
 * <p>Regression net for <b>GR-06</b> (Observation #6, gangs-ranks-mail.md): {@code /glw gang ally abandon} used to
 * call {@link Gang#removeAlly(Gang)} on both gangs and nothing else. The alliance autosave is upsert-only
 * ({@code saveAll} over {@link GangManager}'s {@code buildAllAlliances} supplier), so it can never remove a row —
 * the abandoned alliance came straight back on the next {@code loadAll}. {@code breakAlliance} drops both direction
 * rows through {@link GangAllianceRepositoryContract#delete} as well as clearing memory.
 */
@DisplayName("GangManager.breakAlliance — memory and rows both go")
class GangManagerAllianceTest {

	private RecordingGangAllianceRepository allianceRepository;
	private GangManager                     gangManager;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract());

		allianceRepository = new RecordingGangAllianceRepository();
		gangManager        = new GangManager(mock(IRepository.class), allianceRepository);
	}

	@Test
	@DisplayName("GR-06: both direction rows are deleted through the repository, not just dropped from memory")
	void breakAlliance_deletesBothDirectionRows() {
		Gang alpha = new Gang(1);
		Gang bravo = new Gang(2);
		alpha.addAlly(bravo);
		bravo.addAlly(alpha);

		gangManager.breakAlliance(alpha, bravo);

		assertEquals(2, allianceRepository.deleted().size(),
		             "one delete per stored direction — an upsert-only autosave can never remove these rows");
		assertTrue(allianceRepository.deleted().stream()
		                             .anyMatch(a -> a.gang().getId() == 1 && a.ally().getId() == 2));
		assertTrue(allianceRepository.deleted().stream()
		                             .anyMatch(a -> a.gang().getId() == 2 && a.ally().getId() == 1));
	}

	@Test
	@DisplayName("both gangs stop listing each other in memory")
	void breakAlliance_clearsBothSidesInMemory() {
		Gang alpha = new Gang(3);
		Gang bravo = new Gang(4);
		alpha.addAlly(bravo);
		bravo.addAlly(alpha);

		gangManager.breakAlliance(alpha, bravo);

		assertFalse(alpha.isAlly(bravo));
		assertFalse(bravo.isAlly(alpha));
	}

	@Test
	@DisplayName("unrelated alliances of the same gangs are untouched")
	void breakAlliance_leavesOtherAlliancesAlone() {
		Gang alpha   = new Gang(5);
		Gang bravo   = new Gang(6);
		Gang charlie = new Gang(7);
		alpha.addAlly(bravo);
		bravo.addAlly(alpha);
		alpha.addAlly(charlie);
		charlie.addAlly(alpha);

		gangManager.breakAlliance(alpha, bravo);

		assertTrue(alpha.isAlly(charlie), "the alpha ↔ charlie alliance must survive");
		assertTrue(charlie.isAlly(alpha));
		assertEquals(2, allianceRepository.deleted().size());
		assertTrue(allianceRepository.deleted().stream().noneMatch(a -> a.ally().getId() == charlie.getId()));
	}

	@Test
	@DisplayName("a null side is a no-op rather than an NPE")
	void breakAlliance_nullSide_isNoOp() {
		Gang alpha = new Gang(8);

		assertDoesNotThrow(() -> gangManager.breakAlliance(alpha, null));
		assertDoesNotThrow(() -> gangManager.breakAlliance(null, alpha));
		assertTrue(allianceRepository.deleted().isEmpty());
	}

	@Test
	@DisplayName("GangManager still satisfies GangLookupContract after the change")
	void gangManager_isAGangLookup() {
		assertInstanceOf(GangLookupContract.class, gangManager);
	}

}
