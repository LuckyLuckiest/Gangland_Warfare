package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.gang.chat.ChatChannels;
import org.luckyraven.gangland.gang.chat.ChatSettings;
import org.luckyraven.gangland.gang.chat.ChatSpyService;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** Chat beans: chat channels, the staff chat spy and the {@code Chat:} settings. */
@Configuration
public final class GangChatConfig {

	@Bean
	public ChatSpyService chatSpyService() {
		return new ChatSpyService();
	}

	@Bean
	public ChatSettings chatSettings(GangRules rules) {
		return new ChatSettings(rules);
	}

	@Bean
	public ChatChannels chatChannels(GangChatToggles toggles) {
		return new ChatChannels(toggles);
	}
}
