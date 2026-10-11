package org.luckyraven.gangland.gang.chat;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.data.gang.GangChatChannel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/** Pure recipient sets of a gang or ally line. Main thread only: the callers pass live manager lookups. */
public final class ChatRouting {

	private ChatRouting() {
	}

	/** Who gets a line: {@code members} the normal line, {@code spies} the staff copy (never also a member). */
	public record Delivery(List<Player> members, List<Player> spies) {
	}

	/**
	 * {@code gangOf} returns -1 for a gangless player; {@code allied} tests another gang id against the sender's gang
	 * and only counts on the ALLIES channel.
	 */
	public static Delivery route(GangChatChannel channel, int gangId, Collection<? extends Player> online,
	                             ToIntFunction<Player> gangOf, IntPredicate allied, Set<UUID> spies) {
		List<Player> members = new ArrayList<>();
		List<Player> watchers = new ArrayList<>();
		if (gangId < 0 || channel == GangChatChannel.PUBLIC) {
			return new Delivery(members, watchers);
		}

		for (Player player : online) {
			int gang = gangOf.applyAsInt(player);
			if (gang == gangId || channel == GangChatChannel.ALLIES && gang >= 0 && allied.test(gang)) {
				members.add(player);
			} else if (spies.contains(player.getUniqueId())) {
				watchers.add(player);
			}
		}
		return new Delivery(members, watchers);
	}
}
