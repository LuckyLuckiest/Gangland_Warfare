package org.luckyraven.gangland.gang.chat;

import org.luckyraven.gangland.data.gang.GangChatChannel;

import java.util.UUID;

/**
 * Which channel a player's chat goes to. Seam: lane W5-C adds the ally channel; until then it is gang chat when the
 * player switched it on, public otherwise.
 */
public class ChatChannels {

	private final GangChatToggles toggles;

	public ChatChannels(GangChatToggles toggles) {
		this.toggles = toggles;
	}

	public GangChatChannel chatChannelOf(UUID player) {
		return toggles.isOn(player) ? GangChatChannel.GANG : GangChatChannel.PUBLIC;
	}
}
