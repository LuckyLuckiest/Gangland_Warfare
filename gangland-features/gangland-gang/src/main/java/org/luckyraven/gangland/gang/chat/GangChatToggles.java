package org.luckyraven.gangland.gang.chat;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.gang.file.GangMessages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

/**
 * Who has gang chat switched on (in memory; forgotten on quit), and the gang line itself. Callers pass the online
 * list and the gang lookup, so this class holds no Bukkit statics.
 */
public final class GangChatToggles {

	private final Set<UUID> on = ConcurrentHashMap.newKeySet();

	/** Switches gang chat for {@code player}; returns the new state. */
	public boolean toggle(UUID player) {
		if (on.add(player)) {
			return true;
		}
		on.remove(player);
		return false;
	}

	public boolean isOn(UUID player) {
		return on.contains(player);
	}

	public void forget(UUID player) {
		on.remove(player);
	}

	/**
	 * The online players whose gang is {@code gangId}. {@code gangOf} returns -1 for a gangless player, so a real gang
	 * id never matches one of them.
	 */
	public List<Player> recipients(int gangId, Collection<? extends Player> online, ToIntFunction<Player> gangOf) {
		List<Player> recipients = new ArrayList<>();
		if (gangId < 0) {
			return recipients;
		}
		for (Player player : online) {
			if (gangOf.applyAsInt(player) == gangId) {
				recipients.add(player);
			}
		}
		return recipients;
	}

	/**
	 * The line every gang member sees: {@code [Gang] Rex: hello}. The message goes in after the colour is applied, so
	 * a player's own {@code &} codes stay literal text.
	 */
	public String format(GangMessages messages, String senderName, String message) {
		return messages.text("Social.Chat_Format", "&a[Gang] &f%player%&7: &f%message%", "player", senderName)
		               .replace("%message%", message);
	}

	/** Sends one gang line to every online member of {@code gangId}. */
	public void broadcast(GangMessages messages, int gangId, String senderName, String message,
	                      Collection<? extends Player> online, ToIntFunction<Player> gangOf) {
		String line = format(messages, senderName, message);
		for (Player recipient : recipients(gangId, online, gangOf)) {
			recipient.sendMessage(line);
		}
	}

}
