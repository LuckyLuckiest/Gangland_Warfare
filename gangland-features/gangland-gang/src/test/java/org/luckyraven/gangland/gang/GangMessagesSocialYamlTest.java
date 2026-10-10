package org.luckyraven.gangland.gang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Every {@code Social.*} key the W2-P2 commands read is shipped in {@code gang/gang_messages.yml} (W2-P2). */
@DisplayName("gang_messages.yml - Social section carries every key the gang social commands read")
class GangMessagesSocialYamlTest {

	/** Keys read by GangChatListener, GangChatCommand, GangJoinCommand and GangRecruitingCommand. */
	private static final List<String> SOCIAL_KEYS = List.of(
			"Social.Chat_Format", "Social.Chat_On", "Social.Chat_Off",
			"Social.Recruiting_Open", "Social.Recruiting_Invite", "Social.Recruiting_Now",
			"Social.Joined", "Social.Invite_Only", "Social.Unknown", "Social.Already_In_Gang", "Social.No_Rank");

	@Test
	@DisplayName("every Social key read by the commands is present in the shipped file")
	void everySocialKey_isShipped() throws Exception {
		YamlConfiguration yaml;
		try (InputStream in = getClass().getResourceAsStream("/gang/gang_messages.yml")) {
			assertNotNull(in, "gang/gang_messages.yml must be on the classpath");
			yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}

		for (String key : SOCIAL_KEYS) {
			assertTrue(yaml.isString(key), "missing " + key + " in gang_messages.yml");
		}
	}

}
