package org.luckyraven.gangland.turf.contract;

import org.luckyraven.gangland.persistence.repository.IRepository;
import org.luckyraven.gangland.turf.data.Turf;

/**
 * Turf persistence contract. The concrete {@code TurfRepository} lives in gangland-impl (AbstractRepository subclass,
 * HikariCP-backed) and implements this interface; the turf module only ever sees the contract.
 */
public interface TurfRepositoryContract extends IRepository<Turf> {
}
