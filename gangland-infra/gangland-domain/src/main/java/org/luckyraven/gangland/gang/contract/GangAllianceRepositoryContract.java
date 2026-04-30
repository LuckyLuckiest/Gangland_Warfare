package org.luckyraven.gangland.gang.contract;

import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.persistence.repository.IRepository;

/**
 * Repository contract for gang alliances. Lets the gang manager inject a {@link GangLookupContract} into the alliance
 * repo without casting to the concrete impl-side class.
 */
public interface GangAllianceRepositoryContract extends IRepository<GangAlliance> {

	void setGangLookup(GangLookupContract gangLookup);

	void deleteAllForGang(Gang gang);
}
