package me.luckyraven.gang.contract;

import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangAlliance;
import me.luckyraven.persistence.repository.IRepository;

/**
 * Repository contract for gang alliances. Lets the gang manager inject a {@link GangLookupContract} into the alliance
 * repo without casting to the concrete impl-side class.
 */
public interface GangAllianceRepositoryContract extends IRepository<GangAlliance> {

	void setGangLookup(GangLookupContract gangLookup);

	void deleteAllForGang(Gang gang);
}
