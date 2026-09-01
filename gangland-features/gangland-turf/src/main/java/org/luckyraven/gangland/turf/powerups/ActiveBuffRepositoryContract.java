package org.luckyraven.gangland.turf.powerups;

import org.luckyraven.keystone.persistence.repository.IRepository;

/**
 * Persistence contract for {@link ActiveTurfBuff}. Concrete impl lives in gangland-impl as an
 * {@code AbstractRepository<ActiveTurfBuff>} subclass; the turf module only sees this interface so the feature stays
 * free of HikariCP / SQL types.
 */
public interface ActiveBuffRepositoryContract extends IRepository<ActiveTurfBuff> {
}
