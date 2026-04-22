package me.luckyraven.turf.contract;

import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.turf.data.Turf;

/**
 * Turf persistence contract. The concrete {@code TurfRepository} lives in gangland-impl (AbstractRepository subclass,
 * HikariCP-backed) and implements this interface; the turf module only ever sees the contract.
 */
public interface TurfRepositoryContract extends IRepository<Turf> {
}
