package org.luckyraven.gangland.listener.gang;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.events.gangs.GangLevelReachedEvent;
import org.luckyraven.gangland.events.gangs.GangMemberJoinedEvent;
import org.luckyraven.gangland.events.gangs.GangMemberLeftEvent;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.UUID;

/**
 * Titles for key gang moments (gang_messages.yml {@code Titles}): the gang reached a level, a player joined, the gang
 * was disbanded. Fed by the gangland-api gang events.
 */
@ListenerHandler
public final class GangNotifier implements Listener {

	private static final int FADE_IN = 10;
	private static final int STAY    = 60;
	private static final int FADE_OUT = 20;

	private final GangMessages messages;
	private final GangManager  gangManager;

	public GangNotifier(GangMessages messages, GangManager gangManager) {
		this.messages    = messages;
		this.gangManager = gangManager;
	}

	@EventHandler
	public void onLevel(GangLevelReachedEvent event) {
		Gang gang = gangManager.getGang(event.gangId());
		if (gang == null) return;

		for (Member member : gang.getMembers()) {
			title(member.getUuid(), "Level_Up", gang, event.level(), "&6Level %level%", "&7%gang% &7grew stronger");
		}
	}

	@EventHandler
	public void onJoined(GangMemberJoinedEvent event) {
		title(event.player(), "Joined", gangManager.getGang(event.gangId()), 0, "&aWelcome", "&7You joined %gang%");
	}

	@EventHandler
	public void onLeft(GangMemberLeftEvent event) {
		if (event.reason() != GangMemberLeftEvent.Reason.DISBANDED) return;

		title(event.player(), "Disbanded", gangManager.getGang(event.gangId()), 0, "&cGang disbanded",
		      "&7%gang% &7is no more");
	}

	private void title(UUID uuid, String key, @Nullable Gang gang, int level, String defTitle,
	                   String defSubtitle) {
		Player player = Bukkit.getPlayer(uuid);
		if (player == null) return;

		String name = gang == null ? "" : gang.getDisplayNameString();

		player.sendTitle(messages.text("Titles." + key + ".Title", defTitle, "gang", name, "level", level),
		                 messages.text("Titles." + key + ".Subtitle", defSubtitle, "gang", name, "level", level),
		                 FADE_IN, STAY, FADE_OUT);
	}

}
