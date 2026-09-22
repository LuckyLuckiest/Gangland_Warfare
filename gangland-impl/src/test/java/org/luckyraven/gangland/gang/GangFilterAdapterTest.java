package org.luckyraven.gangland.gang;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.file.configuration.gang.GanglandGangSettings;
import org.luckyraven.gangland.file.configuration.identity.GanglandIdentitySettings;
import org.luckyraven.gangland.menu.filter.StandardFilterField;
import org.luckyraven.gangland.support.SettingsFixture;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins {@link GangFilterAdapter}'s projection of {@link Gang} onto {@link StandardFilterField} after its
 * WS2 G3a move from {@code gangland-domain} (G2) into {@code gangland-impl}'s {@code org.luckyraven.gangland.menu.filter}
 * package — the deferred test from plan §7 / review finding F2.
 */
class GangFilterAdapterTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: SettingsFixture writes a real settings.yml via FileHandler
	Path tempDir;

	private final GangFilterAdapter adapter = new GangFilterAdapter();

	@BeforeEach
	void setUp() {
		// Gang()'s Bounty field reads IdentitySettings (bound to the static Settings reader) at construction time;
		// GangSettings is bound too for the getGangDisplayNameChar()-derived fields this adapter also projects.
		SettingsFixture.initializeMinimal(tempDir);
		GangSettings.bind(new GanglandGangSettings());
		IdentitySettings.bind(new GanglandIdentitySettings());
	}

	@Test
	void name_isLowercasedDisplayName() {
		Gang gang = new Gang(1);
		gang.setName("Zebra Gang");

		assertEquals("zebra gang", adapter.project(gang, StandardFilterField.NAME));
	}

	@Test
	void name_nullDisplayName_projectsEmptyString() {
		Gang gang = new Gang(1);
		// name left null, displayName defaults to "" -> getDisplayNameString() falls back to the null name

		assertEquals("", adapter.project(gang, StandardFilterField.NAME));
	}

	@Test
	void description_projectsRawDescription() {
		Gang gang = new Gang(1);
		gang.setDescription("Running the docks");

		assertEquals("Running the docks", adapter.project(gang, StandardFilterField.DESCRIPTION));
	}

	@Test
	void color_projectsRawColorName() {
		Gang gang = new Gang(1);
		gang.setColor("CRIMSON");

		assertEquals("CRIMSON", adapter.project(gang, StandardFilterField.COLOR));
	}

	@Test
	void members_projectsMemberCount() {
		List<org.luckyraven.gangland.gang.member.Member> members = List.of(
				new org.luckyraven.gangland.gang.member.Member(UUID.randomUUID()),
				new org.luckyraven.gangland.gang.member.Member(UUID.randomUUID()));
		Gang gang = new Gang(1, members);

		assertEquals(2, adapter.project(gang, StandardFilterField.MEMBERS));
	}

	@Test
	void members_emptyList_projectsZero() {
		Gang gang = new Gang(1, Collections.emptyList());

		assertEquals(0, adapter.project(gang, StandardFilterField.MEMBERS));
	}

	@Test
	void date_projectsRawCreatedEpochMillis() {
		Gang gang = new Gang(1);
		gang.setCreated(1_700_000_000_000L);

		assertEquals(1_700_000_000_000L, adapter.project(gang, StandardFilterField.DATE));
	}

	@Test
	void unsupportedField_projectsNull() {
		Gang gang = new Gang(1);

		assertNull(adapter.project(gang, StandardFilterField.PRICE));
	}

	@Test
	void nullGangOrNullField_projectsNull() {
		Gang gang = new Gang(1);

		assertNull(adapter.project(null, StandardFilterField.NAME));
		assertNull(adapter.project(gang, null));
	}

}
