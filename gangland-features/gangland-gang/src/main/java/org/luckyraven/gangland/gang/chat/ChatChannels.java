package org.luckyraven.gangland.gang.chat;

import org.luckyraven.gangland.data.gang.GangChatChannel;

import java.util.UUID;

/**
 * Which channel a player's chat goes to: public, gang or gang plus allies, as the player toggled it.
 */
public class ChatChannels {

	private final GangChatToggles toggles;

	public ChatChannels(GangChatToggles toggles) {
		this.toggles = toggles;
	}

	public GangChatChannel chatChannelOf(UUID player) {
		return toggles.channelOf(player);
	}
}
