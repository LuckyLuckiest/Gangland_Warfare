package org.luckyraven.gangland.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterises the status, expiry, key and type filters of the four finders {@link MailManagerTest} only touches
 * with one happy path each: {@code findPendingForRecipientGang}, {@code findPendingFromSenderGang},
 * {@code findPendingByGangAndRecipient} and {@code findPendingBetweenGangs}. Also pins the shape the callers rely on:
 * the list finders return a mutable list, the Optional finders return the first match in cache order, and the
 * recipient uuid is only dereferenced after the status and expiry filters have passed.
 */
@DisplayName("MailManager - finder filter order and result shape")
class MailManagerFinderFiltersTest {

	private static final long FAR_FUTURE = System.currentTimeMillis() + 3_600_000L;
	private static final long PAST       = System.currentTimeMillis() - 3_600_000L;
	private static final int  SENDER     = 10;
	private static final int  RECIPIENT  = 20;

	private MailManager manager;
	private UUID        player;

	@BeforeEach
	void setUp() {
		manager = new MailManager(new FakeMailRepositoryContract());
		manager.initialize();
		player = UUID.randomUUID();
	}

	private MailItem mail(MailType type, int senderGang, UUID recipient, int recipientGang, long expiresAt,
	                      MailStatus status) {
		MailItem mail = new MailItem(manager.allocateId(), type, null, senderGang, recipient, recipientGang, null, 1L,
		                             expiresAt, status, false);
		manager.send(mail);
		return mail;
	}

	private MailItem ally(int senderGang, int recipientGang, long expiresAt, MailStatus status) {
		return mail(MailType.GANG_ALLY_REQUEST, senderGang, null, recipientGang, expiresAt, status);
	}

	private MailItem invite(int senderGang, UUID recipient, long expiresAt, MailStatus status) {
		return mail(MailType.GANG_INVITE, senderGang, recipient, MailItem.NO_GANG, expiresAt, status);
	}

	private static Set<MailItem> asSet(Collection<MailItem> items) {
		return items.stream().collect(Collectors.toSet());
	}

	/** The first item of the cache, in its iteration order, that satisfies the predicate. */
	private Optional<MailItem> firstInCacheOrder(Predicate<MailItem> predicate) {
		for (MailItem item : manager.getAll()) {
			if (predicate.test(item)) return Optional.of(item);
		}
		return Optional.empty();
	}

	@Test
	@DisplayName("findPendingForRecipientGang keeps only PENDING, unexpired mail for that gang and type; null type = any")
	void findPendingForRecipientGang_appliesEveryFilter() {
		MailItem matches      = ally(SENDER, RECIPIENT, FAR_FUTURE, MailStatus.PENDING);
		MailItem noDeadline   = ally(SENDER + 1, RECIPIENT, 0L, MailStatus.PENDING);
		ally(SENDER, RECIPIENT, FAR_FUTURE, MailStatus.ACCEPTED);
		ally(SENDER, RECIPIENT, PAST, MailStatus.PENDING);
		ally(SENDER, RECIPIENT + 1, FAR_FUTURE, MailStatus.PENDING);
		MailItem otherType = mail(MailType.GENERIC_MESSAGE, SENDER, null, RECIPIENT, FAR_FUTURE, MailStatus.PENDING);

		List<MailItem> ofType = manager.findPendingForRecipientGang(RECIPIENT, MailType.GANG_ALLY_REQUEST);
		List<MailItem> anyType = manager.findPendingForRecipientGang(RECIPIENT, null);

		assertEquals(Set.of(matches, noDeadline), asSet(ofType));
		assertEquals(Set.of(matches, noDeadline, otherType), asSet(anyType));
		ofType.add(matches); // callers own the list: it must stay mutable
		assertEquals(3, ofType.size());
	}

	@Test
	@DisplayName("findPendingFromSenderGang keeps only PENDING, unexpired mail from that gang and type; null type = any")
	void findPendingFromSenderGang_appliesEveryFilter() {
		MailItem matches    = ally(SENDER, RECIPIENT, FAR_FUTURE, MailStatus.PENDING);
		MailItem noDeadline = ally(SENDER, RECIPIENT + 1, 0L, MailStatus.PENDING);
		ally(SENDER, RECIPIENT, FAR_FUTURE, MailStatus.REJECTED);
		ally(SENDER, RECIPIENT, PAST, MailStatus.PENDING);
		ally(SENDER + 1, RECIPIENT, FAR_FUTURE, MailStatus.PENDING);
		MailItem otherType = mail(MailType.GENERIC_MESSAGE, SENDER, null, RECIPIENT, FAR_FUTURE, MailStatus.PENDING);

		List<MailItem> ofType = manager.findPendingFromSenderGang(SENDER, MailType.GANG_ALLY_REQUEST);
		List<MailItem> anyType = manager.findPendingFromSenderGang(SENDER, null);

		assertEquals(Set.of(matches, noDeadline), asSet(ofType));
		assertEquals(Set.of(matches, noDeadline, otherType), asSet(anyType));
		ofType.add(matches);
		assertEquals(3, ofType.size());
	}

	@Test
	@DisplayName("the NO_GANG sentinel short-circuits both gang list finders to an empty list")
	void gangListFinders_noGangSentinel_returnEmpty() {
		mail(MailType.GANG_INVITE, MailItem.NO_GANG, player, MailItem.NO_GANG, FAR_FUTURE, MailStatus.PENDING);

		assertTrue(manager.findPendingForRecipientGang(MailItem.NO_GANG, MailType.GANG_INVITE).isEmpty());
		assertTrue(manager.findPendingFromSenderGang(MailItem.NO_GANG, MailType.GANG_INVITE).isEmpty());
	}

	@Test
	@DisplayName("findPendingByGangAndRecipient skips non-PENDING, expired, other-gang, other-recipient and other-type mail")
	void findPendingByGangAndRecipient_appliesEveryFilter() {
		invite(SENDER, player, FAR_FUTURE, MailStatus.CANCELLED);
		invite(SENDER, player, PAST, MailStatus.PENDING);
		invite(SENDER + 1, player, FAR_FUTURE, MailStatus.PENDING);
		invite(SENDER, UUID.randomUUID(), FAR_FUTURE, MailStatus.PENDING);
		mail(MailType.GENERIC_MESSAGE, SENDER, player, MailItem.NO_GANG, FAR_FUTURE, MailStatus.PENDING);

		assertTrue(manager.findPendingByGangAndRecipient(SENDER, player, MailType.GANG_INVITE).isEmpty());

		MailItem live = invite(SENDER, player, FAR_FUTURE, MailStatus.PENDING);

		assertEquals(Optional.of(live), manager.findPendingByGangAndRecipient(SENDER, player, MailType.GANG_INVITE));
	}

	@Test
	@DisplayName("findPendingByGangAndRecipient returns the first match in cache order; a null type matches nothing")
	void findPendingByGangAndRecipient_returnsFirstMatchInCacheOrder() {
		invite(SENDER, player, FAR_FUTURE, MailStatus.PENDING);
		invite(SENDER, player, 0L, MailStatus.PENDING);
		invite(SENDER, player, FAR_FUTURE, MailStatus.PENDING);

		Optional<MailItem> expected = firstInCacheOrder(
				m -> m.getType() == MailType.GANG_INVITE && m.getSenderGangId() == SENDER);

		assertEquals(expected, manager.findPendingByGangAndRecipient(SENDER, player, MailType.GANG_INVITE));
		assertTrue(manager.findPendingByGangAndRecipient(SENDER, player, null).isEmpty(),
		           "a null type matches nothing here: the type test is mail.getType() != type");
	}

	@Test
	@DisplayName("findPendingByGangAndRecipient dereferences a null recipient only once a mail passed status, expiry and gang")
	void findPendingByGangAndRecipient_nullRecipient_throwsOnlyPastTheEarlierFilters() {
		invite(SENDER, player, PAST, MailStatus.PENDING);
		invite(SENDER, player, FAR_FUTURE, MailStatus.ACCEPTED);
		invite(SENDER + 1, player, FAR_FUTURE, MailStatus.PENDING);

		assertTrue(manager.findPendingByGangAndRecipient(SENDER, null, MailType.GANG_INVITE).isEmpty());

		invite(SENDER, player, FAR_FUTURE, MailStatus.PENDING);

		assertThrows(NullPointerException.class,
		             () -> manager.findPendingByGangAndRecipient(SENDER, null, MailType.GANG_INVITE));
	}

	@Test
	@DisplayName("findPendingBetweenGangs skips non-PENDING, expired, other-sender, other-recipient and other-type mail")
	void findPendingBetweenGangs_appliesEveryFilter() {
		ally(SENDER, RECIPIENT, FAR_FUTURE, MailStatus.REJECTED);
		ally(SENDER, RECIPIENT, PAST, MailStatus.PENDING);
		ally(SENDER + 1, RECIPIENT, FAR_FUTURE, MailStatus.PENDING);
		ally(SENDER, RECIPIENT + 1, FAR_FUTURE, MailStatus.PENDING);
		mail(MailType.GENERIC_MESSAGE, SENDER, null, RECIPIENT, FAR_FUTURE, MailStatus.PENDING);

		assertTrue(manager.findPendingBetweenGangs(SENDER, RECIPIENT, MailType.GANG_ALLY_REQUEST).isEmpty());

		MailItem live = ally(SENDER, RECIPIENT, 0L, MailStatus.PENDING);

		assertEquals(Optional.of(live), manager.findPendingBetweenGangs(SENDER, RECIPIENT, MailType.GANG_ALLY_REQUEST));
	}

	@Test
	@DisplayName("findPendingBetweenGangs returns the first match in cache order")
	void findPendingBetweenGangs_returnsFirstMatchInCacheOrder() {
		ally(SENDER, RECIPIENT, FAR_FUTURE, MailStatus.PENDING);
		ally(SENDER, RECIPIENT, 0L, MailStatus.PENDING);
		ally(SENDER, RECIPIENT, FAR_FUTURE, MailStatus.PENDING);

		Optional<MailItem> expected = firstInCacheOrder(m -> m.getType() == MailType.GANG_ALLY_REQUEST);

		assertEquals(expected, manager.findPendingBetweenGangs(SENDER, RECIPIENT, MailType.GANG_ALLY_REQUEST));
	}

	@Test
	@DisplayName("findPendingForRecipient: the list is a fresh mutable list on every call")
	void findPendingForRecipient_returnsFreshMutableList() {
		MailItem live = invite(SENDER, player, FAR_FUTURE, MailStatus.PENDING);

		List<MailItem> first = manager.findPendingForRecipient(player, MailType.GANG_INVITE);
		first.clear();
		List<MailItem> second = manager.findPendingForRecipient(player, MailType.GANG_INVITE);

		assertEquals(List.of(live), second);
		assertEquals(ArrayList.class, second.getClass());
	}

}
