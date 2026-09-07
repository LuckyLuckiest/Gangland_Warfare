package org.luckyraven.gangland.mail.contract;

import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.keystone.persistence.repository.IRepository;

/**
 * Lets {@code MailManager} sit in the feature module without depending on {@code gangland-impl}'s concrete repository.
 * The impl-side {@code MailRepository} implements this contract directly so it can be cast off the
 * {@code RepositoryRegistry} during bean wiring.
 */
public interface MailRepositoryContract extends IRepository<MailItem> {
}
