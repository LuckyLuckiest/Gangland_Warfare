package org.luckyraven.gangland.gang.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.events.gangs.GangChatEvent;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.data.gang.GangChatChannel;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.level.GangLimits;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/**
 * Who has gang chat switched on (in memory; forgotten on quit), and the gang line itself. Callers pass the online
 * list and the gang lookup, so this class holds no Bukkit statics.
 */
public final class GangChatToggles {

	private final Map<UUID, GangChatChannel> modes = new ConcurrentHashMap<>();

	private final ChatSpyService spy;
	private final GangLimits     limits;
	private final ChatSettings   settings;

	public GangChatToggles() {
		this(new ChatSpyService(), new GangLimits(), new ChatSettings());
	}

	public GangChatToggles(ChatSpyService spy, GangLimits limits, ChatSettings settings) {
		this.spy      = spy;
		this.limits   = limits;
		this.settings = settings;
	}

	/** Switches gang chat for {@code player}; returns the new state. */
	public boolean toggle(UUID player) {
		if (modes.put(player, GangChatChannel.GANG) == GangChatChannel.GANG) {
			modes.remove(player);
			return false;
		}
		return true;
	}

	/** Switches ally chat (gang plus allies) for {@code player}; returns the new state. */
	public boolean toggleAllies(UUID player) {
		if (modes.put(player, GangChatChannel.ALLIES) == GangChatChannel.ALLIES) {
			modes.remove(player);
			return false;
		}
		return true;
	}

	public GangChatChannel channelOf(UUID player) {
		return modes.getOrDefault(player, GangChatChannel.PUBLIC);
	}

	/** Context of one delivery: the online list, the gang lookup and the ally test (main thread). */
	public record Context(Collection<? extends Player> online, ToIntFunction<Player> gangOf, IntPredicate allied) {

		/** Allied test of gang ids against {@code gang}, read live from {@code gangManager}. */
		public static Context of(Collection<? extends Player> online, ToIntFunction<Player> gangOf, Gang gang,
		                         GangManager gangManager) {
			return new Context(online, gangOf, id -> {
				Gang other = gangManager.getGang(id);
				return other != null && gang.isAlly(other);
			});
		}
	}

	/**
	 * Fires {@link GangChatEvent} then delivers the gang or ally line, the staff-spy copies and the console log;
	 * returns false when a handler cancelled it (or the sender has no gang). Main thread only.
	 */
	public boolean deliver(GangMessages messages, Player sender, int gangId, String gangName, GangChatChannel channel,
	                       String message, Context context) {
		if (gangId < 0 || channel == GangChatChannel.PUBLIC) {
			return false;
		}

		GangChatEvent event = new GangChatEvent(sender, gangId, channel, message);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			return false;
		}

		String prefix = prefix(gangId);
		String line = channel == GangChatChannel.ALLIES
		              ? messages.text("Chat.Ally_Format", "&d[Ally] %prefix%&e%gang% &f%player%&7: &f%message%",
		                              "prefix", prefix, "gang", gangName, "player", sender.getName())
		                        .replace("%message%", message)
		              : format(messages, prefix, sender.getName(), message);
		ChatRouting.Delivery delivery = ChatRouting.route(channel, gangId, context.online(), context.gangOf(),
		                                                  context.allied(), spy.spies());
		for (Player recipient : delivery.members()) {
			recipient.sendMessage(line);
		}

		String spyLine = null;
		if (!delivery.spies().isEmpty() || settings.logToConsole()) {
			spyLine = messages.text("Chat.Spy_Format", "&8[Spy] %prefix%&7%gang% &f%player%&7: &f%message%",
			                        "prefix", prefix, "gang", gangName, "player", sender.getName())
			                  .replace("%message%", message);
		}
		for (Player watcher : delivery.spies()) {
			if (!watcher.hasPermission(org.luckyraven.gangland.gang.permission.GangPermissions.SPY)) {
				continue;
			}
			watcher.sendMessage(spyLine);
		}
		if (settings.logToConsole()) {
			Bukkit.getConsoleSender().sendMessage(spyLine);
		}
		return true;
	}

	private String prefix(int gangId) {
		String prefix = limits.chatPrefix(gangId);
		return prefix == null || prefix.isEmpty() ? "" : prefix + " ";
	}

	public boolean isOn(UUID player) {
		return modes.containsKey(player);
	}

	public void forget(UUID player) {
		modes.remove(player);
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
		return format(messages, "", senderName, message);
	}

	private String format(GangMessages messages, String prefix, String senderName, String message) {
		return messages.text("Social.Chat_Format", "&a[Gang] %prefix%&f%player%&7: &f%message%", "prefix", prefix,
		                     "player", senderName)
		               .replace("%message%", message);
	}

}
