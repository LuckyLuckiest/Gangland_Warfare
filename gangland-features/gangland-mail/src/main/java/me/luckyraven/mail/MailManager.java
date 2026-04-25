package me.luckyraven.mail;

import lombok.CustomLog;
import me.luckyraven.mail.contract.MailRepositoryContract;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central registry for {@link MailItem}s. Holds an in-memory cache keyed by id, exposes lookup methods that pending /
 * cancel commands need, and delegates persistence to {@link MailRepositoryContract}.
 *
 * <p>{@link #initialize()} must run after the repository has been registered so the cache is hydrated and the data
 * supplier is set — without the supplier {@code RepositoryRegistry.saveAll()} throws on the next autosave tick.
 *
 * <p>Mail ids are auto-incrementing longs allocated client-side: on init we seed an {@link AtomicLong} to
 * {@code max(existingId) + 1} so ids stay unique across restarts without a DB sequence.
 */
@CustomLog
public final class MailManager {

	private final MailRepositoryContract repository;
	private final Map<Long, MailItem>    mailById = new ConcurrentHashMap<>();
	private final AtomicLong             nextId   = new AtomicLong(1);

	public MailManager(MailRepositoryContract repository) {
		this.repository = repository;
	}

	public void initialize() {
		mailById.clear();

		Collection<MailItem> loaded  = repository.loadAll();
		long                 highest = 0;
		for (MailItem mail : loaded) {
			mailById.put(mail.getId(), mail);
			if (mail.getId() > highest) highest = mail.getId();
		}
		nextId.set(highest + 1);

		repository.setDataSupplier(mailById::values);

		log.debug("Loaded {} mail item(s); next id = {}", mailById.size(), nextId.get());
	}

	public long allocateId() {
		return nextId.getAndIncrement();
	}

	/**
	 * Persists a new mail item. The caller constructs the {@link MailItem} with an id obtained from
	 * {@link #allocateId()}.
	 */
	public void send(MailItem mail) {
		mailById.put(mail.getId(), mail);
		repository.save(mail);
	}

	public Optional<MailItem> findById(long id) {
		return Optional.ofNullable(mailById.get(id));
	}

	public Collection<MailItem> getAll() {
		return Collections.unmodifiableCollection(mailById.values());
	}

	/**
	 * @return pending mail items addressed to the given player UUID, optionally filtered by type. Pass
	 *        {@code type == null} to get every pending mail for the recipient.
	 */
	public List<MailItem> findPendingForRecipient(UUID recipientUuid, MailType type) {
		if (recipientUuid == null) return Collections.emptyList();
		List<MailItem> out = new ArrayList<>();
		for (MailItem mail : mailById.values()) {
			if (mail.getStatus() != MailStatus.PENDING) continue;
			if (mail.isExpired()) continue;
			if (!recipientUuid.equals(mail.getRecipientUuid())) continue;
			if (type != null && mail.getType() != type) continue;
			out.add(mail);
		}
		return out;
	}

	/**
	 * @return pending mail items addressed to the given gang id, optionally filtered by type.
	 */
	public List<MailItem> findPendingForRecipientGang(int recipientGangId, MailType type) {
		if (recipientGangId == MailItem.NO_GANG) return Collections.emptyList();
		List<MailItem> out = new ArrayList<>();
		for (MailItem mail : mailById.values()) {
			if (mail.getStatus() != MailStatus.PENDING) continue;
			if (mail.isExpired()) continue;
			if (mail.getRecipientGangId() != recipientGangId) continue;
			if (type != null && mail.getType() != type) continue;
			out.add(mail);
		}
		return out;
	}

	/**
	 * @return pending mail items sent by the given gang id, optionally filtered by type.
	 */
	public List<MailItem> findPendingFromSenderGang(int senderGangId, MailType type) {
		if (senderGangId == MailItem.NO_GANG) return Collections.emptyList();
		List<MailItem> out = new ArrayList<>();
		for (MailItem mail : mailById.values()) {
			if (mail.getStatus() != MailStatus.PENDING) continue;
			if (mail.isExpired()) continue;
			if (mail.getSenderGangId() != senderGangId) continue;
			if (type != null && mail.getType() != type) continue;
			out.add(mail);
		}
		return out;
	}

	/**
	 * Looks up a single pending mail by sender gang and recipient player UUID — used to detect duplicate gang invites
	 * before sending another, and to find the row that {@code /glw gang invite cancel <player>} should remove.
	 */
	public Optional<MailItem> findPendingByGangAndRecipient(int senderGangId, UUID recipientUuid, MailType type) {
		for (MailItem mail : mailById.values()) {
			if (mail.getStatus() != MailStatus.PENDING) continue;
			if (mail.isExpired()) continue;
			if (mail.getSenderGangId() != senderGangId) continue;
			if (!recipientUuid.equals(mail.getRecipientUuid())) continue;
			if (mail.getType() != type) continue;
			return Optional.of(mail);
		}
		return Optional.empty();
	}

	/**
	 * Looks up a single pending mail between two gangs — used for {@code /glw gang ally pending cancel <gang>} and to
	 * detect duplicate ally requests.
	 */
	public Optional<MailItem> findPendingBetweenGangs(int senderGangId, int recipientGangId, MailType type) {
		for (MailItem mail : mailById.values()) {
			if (mail.getStatus() != MailStatus.PENDING) continue;
			if (mail.isExpired()) continue;
			if (mail.getSenderGangId() != senderGangId) continue;
			if (mail.getRecipientGangId() != recipientGangId) continue;
			if (mail.getType() != type) continue;
			return Optional.of(mail);
		}
		return Optional.empty();
	}

	/**
	 * Marks the mail accepted and removes it from storage. The caller is responsible for performing the side-effect
	 * (adding the gang member, recording the alliance, etc.) before invoking this.
	 */
	public void accept(MailItem mail) {
		finish(mail, MailStatus.ACCEPTED);
	}

	/**
	 * Marks the mail rejected and removes it from storage.
	 */
	public void reject(MailItem mail) {
		finish(mail, MailStatus.REJECTED);
	}

	/**
	 * Marks the mail cancelled (sender-initiated) and removes it from storage.
	 */
	public void cancel(MailItem mail) {
		finish(mail, MailStatus.CANCELLED);
	}

	/**
	 * Sweeps every PENDING mail whose {@code expiresAt} has passed and deletes it. Called from the periodic update
	 * tick; safe to call frequently (cheap when nothing is due).
	 */
	public void expireDue() {
		List<MailItem> due = new ArrayList<>();
		for (MailItem mail : mailById.values()) {
			if (mail.getStatus() != MailStatus.PENDING) continue;
			if (!mail.isExpired()) continue;
			due.add(mail);
		}
		for (MailItem mail : due) {
			finish(mail, MailStatus.EXPIRED);
		}
		if (!due.isEmpty()) {
			log.debug("Expired {} mail item(s)", due.size());
		}
	}

	private void finish(MailItem mail, MailStatus terminalStatus) {
		mail.setStatus(terminalStatus);
		mailById.remove(mail.getId());
		repository.delete(mail);
	}

}
