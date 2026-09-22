package org.luckyraven.gangland.data.gang;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Pins {@link GangItemSourceContributions} — the W54 F1 seam that replaces the deleted
 * {@code GangItemSourceProvider}: the core's {@code ItemSourceProvider} wrapper (see
 * {@code GameplayConfig.inventoryRuntimeContext}) delegates here instead of naming {@code Gang}/{@code Member}
 * directly. Every gang menu (gang_info.yml, gang_stat.yml, phone_gang.yml, phone_gang_search.yml,
 * alliance_stat.yml) rendered zero rows while this seam did not exist — this is the regression net for that.
 */
@DisplayName("GangItemSourceContributions - dispatch by supports(source)")
class GangItemSourceContributionsTest {

	@Test
	@DisplayName("returns the supporting contribution's rows for a source it owns (e.g. gang_members)")
	void entries_returnsSupportingContributionsRows() {
		Player player = mock(Player.class);
		Map<String, String> row = Map.of("member_name", "Alice");

		GangItemSourceContribution gangModule = new GangItemSourceContribution() {
			@Override
			public boolean supports(String source) {
				return "gang_members".equals(source);
			}

			@Override
			public List<Map<String, String>> entries(Player p, String source) {
				return List.of(row);
			}
		};

		GangItemSourceContributions contributions = new GangItemSourceContributions(List.of(gangModule));

		List<Map<String, String>> result = contributions.entries(player, "gang_members");

		assertEquals(1, result.size());
		assertEquals(row, result.get(0));
	}

	@Test
	@DisplayName("returns an empty list when no contribution supports the source (module not installed)")
	void entries_noContributionSupportsSource_returnsEmpty() {
		GangItemSourceContributions contributions = GangItemSourceContributions.none();

		List<Map<String, String>> result = contributions.entries(mock(Player.class), "gang_members");

		assertTrue(result.isEmpty());
	}

	@Test
	@DisplayName("an installed contribution that doesn't support the requested source is skipped, not asked")
	void entries_installedButUnsupportedSource_returnsEmpty() {
		GangItemSourceContribution onlyGangs = new GangItemSourceContribution() {
			@Override
			public boolean supports(String source) {
				return "gangs".equals(source);
			}

			@Override
			public List<Map<String, String>> entries(Player p, String source) {
				return List.of(Map.of("gang_id", "1"));
			}
		};

		GangItemSourceContributions contributions = new GangItemSourceContributions(List.of(onlyGangs));

		assertTrue(contributions.entries(mock(Player.class), "gang_members").isEmpty(),
		           "a contribution that only supports() \"gangs\" must not answer for \"gang_members\"");
	}

}
