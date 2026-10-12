package org.luckyraven.gangland.listener.gang;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.chat.ChatSettings;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replaces {@code {glw_gang}} in the public chat format with the sender's level prefix and gang name, when
 * {@code Chat.Public_Tag.Enabled}. The format is a {@code String.format} pattern, so every spliced value has its
 * {@code %} doubled.
 */
@ListenerHandler
public class GangPublicTagListener implements Listener {

	private static final String TOKEN = "{glw_gang}";

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;
	private final GangLimits          limits;
	private final ChatSettings        settings;
	// Coloured tags per online player, written on the main thread only; the async chat thread reads just this map.
	private final Map<UUID, String>   tags = new ConcurrentHashMap<>();

	public GangPublicTagListener(JavaPlugin plugin, @Qualifier("online") UserManager<Player> userManager,
	                             GangManager gangManager, GangLimits limits,
	                             ChatSettings settings) {
		this.userManager = userManager;
		this.gangManager = gangManager;
		this.limits      = limits;
		this.settings    = settings;
		// ponytail: the async chat thread sees a tag up to 1 s stale; event-driven invalidation if that ever matters
		Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 20L, 20L);
	}

	void refresh() {
		if (!settings.publicTagEnabled()) {
			tags.clear();
			return;
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			tags.put(player.getUniqueId(), tag(player));
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		if (settings.publicTagEnabled()) {
			tags.put(event.getPlayer().getUniqueId(), tag(event.getPlayer()));
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		tags.remove(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onChat(AsyncPlayerChatEvent event) {
		if (!settings.publicTagEnabled() || !event.getFormat().contains(TOKEN)) {
			return;
		}

		String tag = event.isAsynchronous()
		             ? tags.getOrDefault(event.getPlayer().getUniqueId(), GanglandChatUtil.color(settings.noGangTag()))
		             : tag(event.getPlayer());
		event.setFormat(event.getFormat().replace(TOKEN, tag.replace("%", "%%")));
	}

	private String tag(Player player) {
		User<Player> user = userManager.getUser(player);
		Gang         gang = user == null || !user.hasGang() ? null : gangManager.getGang(user.getGangId());
		if (gang == null) {
			return GanglandChatUtil.color(settings.noGangTag());
		}

		String prefix = limits.chatPrefix(user.getGangId());
		return GanglandChatUtil.color((prefix == null || prefix.isEmpty() ? "" : prefix + " ")
		                              + gang.getDisplayNameString());
	}
}
