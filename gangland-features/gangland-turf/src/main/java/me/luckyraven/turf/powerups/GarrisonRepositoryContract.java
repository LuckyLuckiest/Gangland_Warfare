package me.luckyraven.turf.powerups;

import me.luckyraven.persistence.repository.IRepository;

/**
 * Persistence contract for {@link Garrison}. Concrete impl lives in gangland-impl as an
 * {@code AbstractRepository<Garrison>} subclass; the turf module only sees this interface.
 */
public interface GarrisonRepositoryContract extends IRepository<Garrison> {
}
