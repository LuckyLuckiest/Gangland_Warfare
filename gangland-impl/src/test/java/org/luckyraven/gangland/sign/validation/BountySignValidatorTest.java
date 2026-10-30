package org.luckyraven.gangland.sign.validation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Line 2 of a {@code [BOUNTY]} sign is checked on creation only: {@code pay} (the pay-off) and {@code view} are the
 * operations, {@code clear} stays accepted so a sign written before the pay-off was named keeps being creatable.
 */
@DisplayName("BountySignValidator - line 2")
class BountySignValidatorTest {

	@TempDir
	Path dir;

	private BukkitStatics        bukkit;
	private BountySignValidator  validator;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(dir);

		validator = new BountySignValidator(new SignType("[Bounty]", "glw-bounty"));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("pay is accepted, in any case")
	void acceptsPay() {
		assertTrue(validator.isValidContent("pay"));
		assertTrue(validator.isValidContent("PAY"));
	}

	@Test
	@DisplayName("view and the legacy clear are still accepted")
	void acceptsViewAndLegacyClear() {
		assertTrue(validator.isValidContent("view"));
		assertTrue(validator.isValidContent("clear"));
	}

	@Test
	@DisplayName("anything else is rejected")
	void rejectsOtherContent() {
		assertFalse(validator.isValidContent("bogus"));
		assertFalse(validator.isValidContent(""));
	}

}
