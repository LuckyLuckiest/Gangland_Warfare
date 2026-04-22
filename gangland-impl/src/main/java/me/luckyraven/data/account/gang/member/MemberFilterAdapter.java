package me.luckyraven.data.account.gang.member;

import me.luckyraven.data.rank.Rank;
import me.luckyraven.inventory.filter.FilterAdapter;
import me.luckyraven.inventory.filter.FilterField;
import me.luckyraven.inventory.filter.StandardFilterField;
import org.bukkit.Bukkit;

import java.util.Locale;

/**
 * Projects {@link Member} instances onto the canonical filter fields used by the gang-members list view. The
 * {@code NAME} projection reads the {@link org.bukkit.OfflinePlayer} name (so the filter matches what's rendered in the
 * inventory), {@code DATE} uses the raw epoch-millis join timestamp for numeric sort, and {@code MEMBERS} is overloaded
 * here as the contribution value (the view's "by contribution" sort axis).
 */
public final class MemberFilterAdapter implements FilterAdapter<Member> {

	@Override
	public Object project(Member member, FilterField field) {
		if (member == null || field == null) return null;
		if (field instanceof StandardFilterField std) {
			return switch (std) {
				case NAME -> {
					String name = Bukkit.getOfflinePlayer(member.getUuid()).getName();
					yield name == null ? "" : name.toLowerCase(Locale.ROOT);
				}
				case CATEGORY -> {
					Rank rank = member.getRank();
					yield rank == null ? "" : rank.getName();
				}
				case MEMBERS -> member.getContribution();
				case DATE -> member.getGangJoinDateLong();
				default -> null;
			};
		}
		return null;
	}

}
