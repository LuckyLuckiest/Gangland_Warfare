package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.gang.chat.ChatChannels;
import org.luckyraven.gangland.gang.chat.ChatSpyService;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** Lane W5-C beans: chat channels and the staff chat spy. The channel body arrives with that lane. */
@Configuration
public final class GangChatConfig {

	@Bean
	public ChatSpyService chatSpyService() {
		return new ChatSpyService();
	}

	@Bean
	public ChatChannels chatChannels(GangChatToggles toggles) {
		return new ChatChannels(toggles);
	}
}
