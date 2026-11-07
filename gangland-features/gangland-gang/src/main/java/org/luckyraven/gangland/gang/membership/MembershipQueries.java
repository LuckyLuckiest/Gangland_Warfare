package org.luckyraven.gangland.gang.membership;

import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only gang roster questions the installed {@code GangMembershipView} answers. */
public class MembershipQueries {

	private final GangManager gangManager;

	public MembershipQueries(GangManager gangManager) {
		this.gangManager = gangManager;
	}

	public List<UUID> membersOf(int gangId) {
		Gang gang = gangManager.getGang(gangId);
		if (gang == null) return List.of();

		List<UUID> members = new ArrayList<>();
		for (Member member : gang.getMembers()) {
			members.add(member.getUuid());
		}
		return members;
	}

	/** The member holding the tail rank, or empty. */
	public Optional<UUID> ownerOf(int gangId) {
		Gang gang = gangManager.getGang(gangId);
		if (gang == null) return Optional.empty();

		String tail = org.luckyraven.gangland.gang.GangSettings.getGangRankTail();
		Member owner = firstWithRank(gang.getMembers(), tail);
		return owner == null ? Optional.empty() : Optional.of(owner.getUuid());
	}

	/** The first member whose rank is set and named {@code rankName} (ignoring case), or {@code null}. */
	public static Member firstWithRank(Collection<Member> members, String rankName) {
		for (Member member : members) {
			if (member.getRank() != null && member.getRank().getName().equalsIgnoreCase(rankName)) {
				return member;
			}
		}
		return null;
	}

	public Set<Integer> gangIds() {
		return new HashSet<>(gangManager.getGangs().keySet());
	}

	/** The id of the gang with this name (ignoring case), or {@code -1}. */
	public int gangIdByName(String name) {
		if (name == null) return -1;

		String wanted = name.toLowerCase(Locale.ROOT);
		for (Gang gang : gangManager.getAll()) {
			if (gang.getName() != null && gang.getName().toLowerCase(Locale.ROOT).equals(wanted)) {
				return gang.getId();
			}
		}
		return -1;
	}
}
