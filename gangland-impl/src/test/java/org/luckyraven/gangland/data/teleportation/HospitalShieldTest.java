package org.luckyraven.gangland.data.teleportation;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The hospital respawn shield's bookkeeping: a grant lasts exactly {@code Shield_Seconds}, 0 or less grants nothing,
 * and the grant tells the player how long. The clock is injected, so nothing here waits.
 */
@DisplayName("HospitalShield - grant, expiry and the off switch")
class HospitalShieldTest {

	@TempDir
	Path tempDir;

	private final AtomicLong clock = new AtomicLong(1_000_000L);

	private HospitalShield shield;
	private Player         player;
	private UUID           uuid;

	@BeforeEach
	void setUp() {
		Messages.init(new FakeMessageProvider().withString("Death.Hospital_Shield", "Protected for %seconds% s"));
		shield = new HospitalShield(clock::get);
		uuid   = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
	}

	private void seconds(int seconds) throws IOException {
		SettingsFixture.write(tempDir, """
				Money_Symbol: '$'
				Database:
				  Auto_Save:
				    Debug: false
				User:
				  Death:
				    Hospital:
				      Enable: true
				      Shield_Seconds: %d
				""".formatted(seconds));
		SettingsFixture.initialize(tempDir);
	}

	@Test
	@DisplayName("shielded at +4.9 s, not at +5.0 s, and the entry is gone")
	void expires() throws IOException {
		seconds(5);
		shield.grant(player);

		clock.addAndGet(4_900L);
		assertTrue(shield.isShielded(uuid));

		clock.addAndGet(100L);
		assertFalse(shield.isShielded(uuid));
		clock.addAndGet(-100L);
		assertFalse(shield.isShielded(uuid), "an expired entry is removed, not just reported expired");
	}

	@Test
	@DisplayName("Shield_Seconds 0 or -1 stores nothing and sends no message")
	void zeroDisables() throws IOException {
		seconds(0);
		shield.grant(player);
		seconds(-1);
		shield.grant(player);

		assertFalse(shield.isShielded(uuid));
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("the grant tells the player the whole seconds")
	void grant_sendsTheShieldMessage() throws IOException {
		seconds(5);

		shield.grant(player);

		verify(player).sendMessage(contains("Protected for 5 s"));
	}

	@Test
	@DisplayName("end() drops the shield at once")
	void end_dropsTheShield() throws IOException {
		seconds(5);
		shield.grant(player);

		shield.end(uuid);

		assertFalse(shield.isShielded(uuid));
	}

}
