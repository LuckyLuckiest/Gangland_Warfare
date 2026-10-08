package org.luckyraven.gangland.copsncrooks.wanted.hud;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** {@link TitleCue}: placeholder filling, the enabled switch, and the blank check that decides whether anything is sent. */
@DisplayName("TitleCue")
class TitleCueTest {

	@Test
	@DisplayName("an enabled cue fills the placeholders, passes its fades, and reports that it sent")
	void send_enabled_substitutesPlaceholders_passesFades() {
		Player player = mock(Player.class);
		TitleCue cue = new TitleCue(true, "&c%stars% L%level%", "%card%", 2, 30, 3);

		boolean sent = cue.send(player, Map.of("stars", "★★", "level", "2", "card", "Card line"));

		ArgumentCaptor<String> title    = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> subtitle = ArgumentCaptor.forClass(String.class);
		verify(player).sendTitle(title.capture(), subtitle.capture(), eq(2), eq(30), eq(3));
		assertEquals("★★ L2", ChatColor.stripColor(title.getValue()));
		assertEquals("Card line", ChatColor.stripColor(subtitle.getValue()));
		assertTrue(sent);
	}

	@Test
	@DisplayName("a disabled cue never calls sendTitle and reports that it did not send")
	void send_disabled_neverCallsSendTitle() {
		Player player = mock(Player.class);
		TitleCue cue = new TitleCue(false, "&cTitle", "Subtitle", 5, 20, 5);

		boolean sent = cue.send(player, Map.of("stars", "*"));

		verify(player, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
		assertFalse(sent);
	}

	@Test
	@DisplayName("a title and subtitle that are both blank after placeholders never call sendTitle")
	void send_blankTitleAndSubtitle_neverCallsSendTitle() {
		Player player = mock(Player.class);
		TitleCue cue = new TitleCue(true, "", "%card%", 5, 20, 5);

		boolean sent = cue.send(player, Map.of("card", ""));

		verify(player, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
		assertFalse(sent);
	}

	@Test
	@DisplayName("an empty title with a subtitle sends the subtitle with an empty title")
	void send_subtitleOnly_sendsEmptyTitle() {
		Player player = mock(Player.class);
		TitleCue cue = new TitleCue(true, "", "x", 5, 20, 5);

		boolean sent = cue.send(player, Map.of());

		verify(player).sendTitle("", "x", 5, 20, 5);
		assertTrue(sent);
	}

	@Test
	@DisplayName("a %card% that resolves to empty with an empty title sends nothing")
	void send_cardPlaceholderEmpty_resolvesBlank() {
		Player player = mock(Player.class);
		TitleCue cue = new TitleCue(true, "", "%card%", 5, 20, 5);

		boolean sent = cue.send(player, Map.of("card", ""));

		verify(player, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
		assertFalse(sent);
	}
}
