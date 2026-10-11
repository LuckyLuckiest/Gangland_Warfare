package org.luckyraven.gangland.listener.gang;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.chat.ChatSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

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

	public GangPublicTagListener(JavaPlugin plugin, @Qualifier("online") UserManager<Player> userManager,
	                             GangManager gangManager, GangLimits limits, GangMessages messages,
	                             ChatSettings settings) {
		this.userManager = userManager;
		this.gangManager = gangManager;
		this.limits      = limits;
		this.settings    = settings;
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onChat(AsyncPlayerChatEvent event) {
		if (!settings.publicTagEnabled() || !event.getFormat().contains(TOKEN)) {
			return;
		}

		event.setFormat(event.getFormat().replace(TOKEN, tag(event.getPlayer()).replace("%", "%%")));
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
