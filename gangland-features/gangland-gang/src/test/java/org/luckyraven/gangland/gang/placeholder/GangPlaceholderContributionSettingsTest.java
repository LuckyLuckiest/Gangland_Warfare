package org.luckyraven.gangland.gang.placeholder;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GangPlaceholderContributionSettingsTest {

	private GangPlaceholderContribution contribution;
	private OfflinePlayer               player;

	@BeforeEach
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract().withCreateFee(new BigDecimal("2500")).withRankHead("recruit"));
		player = mock(OfflinePlayer.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		contribution = new GangPlaceholderContribution(null, mock(MemberManager.class), mock(GangManager.class));
	}

	@Test
	void createFeeComesFromGangSettingsEvenWithoutAGang() {
		assertEquals("2500", contribution.resolve(player, "gang_create_fee"));
	}

	@Test
	void otherMovedSettingsAreAnswered() {
		assertEquals("recruit", contribution.resolve(player, "gang_rank_head"));
		assertEquals("owner", contribution.resolve(player, "gang_rank_tail"));
		assertEquals("*", contribution.resolve(player, "gang_display_name_char"));
		assertEquals("false", contribution.resolve(player, "gang_name_duplicates"));
		assertEquals("0", contribution.resolve(player, "gang_initial_balance"));
	}

	@Test
	void gangPlaceholderWithoutGangStaysNull() {
		assertNull(contribution.resolve(player, "gang_name"));
	}

}
