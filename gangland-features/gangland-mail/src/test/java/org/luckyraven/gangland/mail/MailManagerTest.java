package org.luckyraven.gangland.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link MailManager} end-to-end against a fake {@link org.luckyraven.gangland.mail.contract.MailRepositoryContract},
 * per the gangs-ranks-mail.md Test Surface bullet "MailManager end-to-end against a fake MailRepositoryContract:
 * allocateId seeding, all five findPending* filters (status, expiry, type, id matching), accept/reject/cancel
 * deleting, and expireDue sweeping only PENDING &amp;&amp; isExpired()". Also pins the unguarded null-recipient NPE the
 * same section calls out for {@link MailManager#findPendingByGangAndRecipient}.
 */
@DisplayName("MailManager - cache, id allocation, lookup filters and lifecycle")
class MailManagerTest {

	private static final long FAR_FUTURE = System.currentTimeMillis() + 60_000L;
	private static final long PAST       = System.currentTimeMillis() - 1L;

	private MailItem mail(long id, MailType type, UUID senderUuid, int senderGang,
	                      UUID recipientUuid, int recipientGang, long expiresAt, MailStatus status) {
		return new MailItem(id, type, senderUuid, senderGang, recipientUuid, recipientGang, null,
				System.currentTimeMillis(), expiresAt, status, false);
	}

	@Nested
	@DisplayName("initialize() / allocateId()")
	class InitializeTest {

		@Test
		@DisplayName("with no persisted mail, ids are allocated starting at 1")
		void allocateId_startsAtOne_whenNothingPersisted() {
			MailManager manager = new MailManager(new FakeMailRepositoryContract());
			manager.initialize();

			assertEquals(1L, manager.allocateId());
			assertEquals(2L, manager.allocateId());
		}

		@Test
		@DisplayName("with persisted mail, the id counter seeds to max(existing id) + 1, not to seed count + 1")
		void allocateId_seedsToMaxExistingIdPlusOne() {
			FakeMailRepositoryContract repo = new FakeMailRepositoryContract(
					mail(3L, MailType.GANG_INVITE, UUID.randomUUID(), MailItem.NO_GANG, UUID.randomUUID(), MailItem.NO_GANG, 0L, MailStatus.PENDING),
					mail(7L, MailType.GANG_INVITE, UUID.randomUUID(), MailItem.NO_GANG, UUID.randomUUID(), MailItem.NO_GANG, 0L, MailStatus.PENDING),
					mail(2L, MailType.GANG_INVITE, UUID.randomUUID(), MailItem.NO_GANG, UUID.randomUUID(), MailItem.NO_GANG, 0L, MailStatus.PENDING));

			MailManager manager = new MailManager(repo);
			manager.initialize();

			assertEquals(8L, manager.allocateId());
		}

		@Test
		void initialize_wiresTheDataSupplierBackOntoTheRepository() {
			FakeMailRepositoryContract repo = new FakeMailRepositoryContract();
			MailManager manager = new MailManager(repo);

			assertFalse(repo.hasDataSupplier());
			manager.initialize();
			assertTrue(repo.hasDataSupplier(), "without this, repositoryRegistry.saveAll() throws on the next autosave tick");
		}

	}

	@Nested
	@DisplayName("send / findById")
	class SendTest {

		@Test
		void send_cachesTheItemAndPersistsThroughTheRepository() {
			FakeMailRepositoryContract repo = new FakeMailRepositoryContract();
			MailManager manager = new MailManager(repo);
			manager.initialize();

			MailItem invite = mail(manager.allocateId(), MailType.GANG_INVITE,
					UUID.randomUUID(), 1, UUID.randomUUID(), MailItem.NO_GANG, FAR_FUTURE, MailStatus.PENDING);

			manager.send(invite);

			assertEquals(Optional.of(invite), manager.findById(invite.getId()));
			assertTrue(repo.saved().contains(invite));
		}

		@Test
		void findById_unknownId_isEmpty() {
			MailManager manager = new MailManager(new FakeMailRepositoryContract());
			manager.initialize();

			assertTrue(manager.findById(999L).isEmpty());
		}

	}

	@Nested
	@DisplayName("findPending* filters")
	class FindPendingTest {

		private MailManager manager;
		private UUID         recipient;
		private int          senderGang;
		private int          recipientGang;

		@BeforeEach
		void seed() {
			manager = new MailManager(new FakeMailRepositoryContract());
			manager.initialize();
			recipient     = UUID.randomUUID();
			senderGang    = 10;
			recipientGang = 20;
		}

		@Test
		@DisplayName("findPendingForRecipient filters out non-PENDING, expired, wrong-recipient and wrong-type entries")
		void findPendingForRecipient_appliesAllFourFilters() {
			MailItem matches       = mail(manager.allocateId(), MailType.GANG_INVITE, UUID.randomUUID(), senderGang, recipient, MailItem.NO_GANG, FAR_FUTURE, MailStatus.PENDING);
			MailItem wrongStatus   = mail(manager.allocateId(), MailType.GANG_INVITE, UUID.randomUUID(), senderGang, recipient, MailItem.NO_GANG, FAR_FUTURE, MailStatus.ACCEPTED);
			MailItem expired       = mail(manager.allocateId(), MailType.GANG_INVITE, UUID.randomUUID(), senderGang, recipient, MailItem.NO_GANG, PAST, MailStatus.PENDING);
			MailItem wrongTarget   = mail(manager.allocateId(), MailType.GANG_INVITE, UUID.randomUUID(), senderGang, UUID.randomUUID(), MailItem.NO_GANG, FAR_FUTURE, MailStatus.PENDING);
			MailItem wrongType     = mail(manager.allocateId(), MailType.GENERIC_MESSAGE, UUID.randomUUID(), senderGang, recipient, MailItem.NO_GANG, FAR_FUTURE, MailStatus.PENDING);

			manager.send(matches);
			manager.send(wrongStatus);
			manager.send(expired);
			manager.send(wrongTarget);
			manager.send(wrongType);

			List<MailItem> result = manager.findPendingForRecipient(recipient, MailType.GANG_INVITE);

			assertEquals(List.of(matches), result);
		}

		@Test
		void findPendingForRecipient_nullTypeMeansAnyType() {
			MailItem invite  = mail(manager.allocateId(), MailType.GANG_INVITE, UUID.randomUUID(), senderGang, recipient, MailItem.NO_GANG, FAR_FUTURE, MailStatus.PENDING);
			MailItem generic = mail(manager.allocateId(), MailType.GENERIC_MESSAGE, UUID.randomUUID(), senderGang, recipient, MailItem.NO_GANG, FAR_FUTURE, MailStatus.PENDING);
			manager.send(invite);
			manager.send(generic);

			assertEquals(2, manager.findPendingForRecipient(recipient, null).size());
		}

		@Test
		void findPendingForRecipient_nullRecipient_returnsEmptyListInsteadOfThrowing() {
			assertTrue(manager.findPendingForRecipient(null, MailType.GANG_INVITE).isEmpty());
		}

		@Test
		void findPendingForRecipientGang_noGangSentinel_returnsEmptyList() {
			assertTrue(manager.findPendingForRecipientGang(MailItem.NO_GANG, null).isEmpty());
		}

		@Test
		void findPendingFromSenderGang_filtersBySenderGangIdAndType() {
			MailItem fromThisGang  = mail(manager.allocateId(), MailType.GANG_ALLY_REQUEST, UUID.randomUUID(), senderGang, UUID.randomUUID(), recipientGang, FAR_FUTURE, MailStatus.PENDING);
			MailItem fromOtherGang = mail(manager.allocateId(), MailType.GANG_ALLY_REQUEST, UUID.randomUUID(), 99, UUID.randomUUID(), recipientGang, FAR_FUTURE, MailStatus.PENDING);
			manager.send(fromThisGang);
			manager.send(fromOtherGang);

			assertEquals(List.of(fromThisGang), manager.findPendingFromSenderGang(senderGang, MailType.GANG_ALLY_REQUEST));
		}

		@Test
		void findPendingByGangAndRecipient_matchesOnSenderGangRecipientUuidAndType() {
			MailItem invite = mail(manager.allocateId(), MailType.GANG_INVITE, UUID.randomUUID(), senderGang, recipient, MailItem.NO_GANG, FAR_FUTURE, MailStatus.PENDING);
			manager.send(invite);

			assertEquals(Optional.of(invite), manager.findPendingByGangAndRecipient(senderGang, recipient, MailType.GANG_INVITE));
			assertTrue(manager.findPendingByGangAndRecipient(senderGang, UUID.randomUUID(), MailType.GANG_INVITE).isEmpty());
		}

		@Test
		@DisplayName("Test Surface note (gangs-ranks-mail.md, MailManager:126): findPendingByGangAndRecipient has no null guard "
		             + "on recipientUuid, unlike findPendingForRecipient - a null argument NPEs instead of returning empty")
		void findPendingByGangAndRecipient_nullRecipientUuid_throwsNpe() {
			MailItem invite = mail(manager.allocateId(), MailType.GANG_INVITE, UUID.randomUUID(), senderGang, recipient, MailItem.NO_GANG, FAR_FUTURE, MailStatus.PENDING);
			manager.send(invite);

			assertThrows(NullPointerException.class,
					() -> manager.findPendingByGangAndRecipient(senderGang, null, MailType.GANG_INVITE));
		}

		@Test
		void findPendingBetweenGangs_matchesOnBothGangIdsAndType() {
			MailItem allyRequest = mail(manager.allocateId(), MailType.GANG_ALLY_REQUEST, UUID.randomUUID(), senderGang, UUID.randomUUID(), recipientGang, FAR_FUTURE, MailStatus.PENDING);
			manager.send(allyRequest);

			assertEquals(Optional.of(allyRequest), manager.findPendingBetweenGangs(senderGang, recipientGang, MailType.GANG_ALLY_REQUEST));
			assertTrue(manager.findPendingBetweenGangs(recipientGang, senderGang, MailType.GANG_ALLY_REQUEST).isEmpty(),
					"direction matters - sender/recipient are not interchangeable");
		}

	}

	@Nested
	@DisplayName("accept / reject / cancel / expireDue")
	class LifecycleTest {

		@Test
		void accept_setsStatus_removesFromCache_andDeletesFromRepository() {
			FakeMailRepositoryContract repo = new FakeMailRepositoryContract();
			MailManager manager = new MailManager(repo);
			manager.initialize();
			MailItem invite = mail(manager.allocateId(), MailType.GANG_INVITE, UUID.randomUUID(), 1, UUID.randomUUID(), MailItem.NO_GANG, 0L, MailStatus.PENDING);
			manager.send(invite);

			manager.accept(invite);

			assertEquals(MailStatus.ACCEPTED, invite.getStatus());
			assertTrue(manager.findById(invite.getId()).isEmpty(), "finish() removes the row from the cache");
			assertTrue(repo.deleted().contains(invite), "nothing is archived - accept deletes the row (gangs-ranks-mail.md W12)");
		}

		@Test
		void reject_setsStatusRejected_andDeletes() {
			FakeMailRepositoryContract repo = new FakeMailRepositoryContract();
			MailManager manager = new MailManager(repo);
			manager.initialize();
			MailItem request = mail(manager.allocateId(), MailType.GANG_ALLY_REQUEST, UUID.randomUUID(), 1, UUID.randomUUID(), 2, 0L, MailStatus.PENDING);
			manager.send(request);

			manager.reject(request);

			assertEquals(MailStatus.REJECTED, request.getStatus());
			assertTrue(repo.deleted().contains(request));
		}

		@Test
		void cancel_setsStatusCancelled_andDeletes() {
			FakeMailRepositoryContract repo = new FakeMailRepositoryContract();
			MailManager manager = new MailManager(repo);
			manager.initialize();
			MailItem invite = mail(manager.allocateId(), MailType.GANG_INVITE, UUID.randomUUID(), 1, UUID.randomUUID(), MailItem.NO_GANG, 0L, MailStatus.PENDING);
			manager.send(invite);

			manager.cancel(invite);

			assertEquals(MailStatus.CANCELLED, invite.getStatus());
			assertTrue(repo.deleted().contains(invite));
		}

		@Test
		@DisplayName("expireDue sweeps only PENDING && isExpired() items - non-pending and not-yet-expired items are left alone")
		void expireDue_sweepsOnlyPendingAndExpiredItems() {
			FakeMailRepositoryContract repo = new FakeMailRepositoryContract();
			MailManager manager = new MailManager(repo);
			manager.initialize();

			MailItem duePending    = mail(manager.allocateId(), MailType.GANG_INVITE, UUID.randomUUID(), 1, UUID.randomUUID(), MailItem.NO_GANG, PAST, MailStatus.PENDING);
			MailItem notYetDue     = mail(manager.allocateId(), MailType.GANG_INVITE, UUID.randomUUID(), 1, UUID.randomUUID(), MailItem.NO_GANG, FAR_FUTURE, MailStatus.PENDING);
			MailItem alreadyAccepted = mail(manager.allocateId(), MailType.GANG_INVITE, UUID.randomUUID(), 1, UUID.randomUUID(), MailItem.NO_GANG, PAST, MailStatus.ACCEPTED);
			manager.send(duePending);
			manager.send(notYetDue);
			manager.send(alreadyAccepted);

			manager.expireDue();

			assertEquals(MailStatus.EXPIRED, duePending.getStatus());
			assertTrue(manager.findById(duePending.getId()).isEmpty());
			assertTrue(repo.deleted().contains(duePending));

			assertEquals(MailStatus.PENDING, notYetDue.getStatus(), "not expired yet - left alone");
			assertTrue(manager.findById(notYetDue.getId()).isPresent());

			assertEquals(MailStatus.ACCEPTED, alreadyAccepted.getStatus(), "not PENDING - expireDue never touches it");
			assertFalse(repo.deleted().contains(alreadyAccepted));
		}

		@Test
		@DisplayName("a paused mail past its deadline is never swept by expireDue (paused-overrides-deadline rule)")
		void expireDue_neverSweepsAPausedItem_evenPastDeadline() {
			FakeMailRepositoryContract repo = new FakeMailRepositoryContract();
			MailManager manager = new MailManager(repo);
			manager.initialize();

			MailItem pausedPastDeadline = mail(manager.allocateId(), MailType.GANG_ALLY_REQUEST, UUID.randomUUID(), 1, UUID.randomUUID(), 2, PAST, MailStatus.PENDING);
			pausedPastDeadline.setPausedAt(System.currentTimeMillis());
			manager.send(pausedPastDeadline);

			manager.expireDue();

			assertEquals(MailStatus.PENDING, pausedPastDeadline.getStatus());
			assertTrue(manager.findById(pausedPastDeadline.getId()).isPresent());
		}

	}

}
