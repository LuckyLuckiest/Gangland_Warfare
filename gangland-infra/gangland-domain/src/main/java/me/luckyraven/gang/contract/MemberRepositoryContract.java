package me.luckyraven.gang.contract;

import me.luckyraven.gang.member.Member;
import me.luckyraven.persistence.repository.IRepository;

/**
 * Repository contract for members. Lets the member manager inject rank / gang lookups into the member repo without
 * importing concrete impl-side classes.
 */
public interface MemberRepositoryContract extends IRepository<Member> {

	void setRankLookup(RankLookupContract rankLookup);

	void setGangLookup(GangLookupContract gangLookup);
}
