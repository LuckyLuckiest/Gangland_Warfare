package org.luckyraven.gangland.file.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.message.MessageProvider;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how a list-valued {@link Messages} entry ({@code LEVEL_STATS}) joins the lines of the language file into one
 * string: a newline between lines, none at the end, the text {@code null} for a null line, an empty string for no lines.
 */
@DisplayName("Messages - joining a list-valued entry into one string")
class MessagesListJoinTest {

	private MessageProvider provider;

	@BeforeEach
	void wireProvider() {
		provider = mock(MessageProvider.class);
		Messages.init(provider);
	}

	@AfterEach
	void unwireProvider() throws Exception {
		Field field = Messages.class.getDeclaredField("provider");
		field.setAccessible(true);
		field.set(null, null);
	}

	private String join(List<String> lines) {
		when(provider.getStringList(Messages.LEVEL_STATS.getPath())).thenReturn(lines);
		return Messages.LEVEL_STATS.toString(Messages.Type.NO_CHANGE);
	}

	@Test
	@DisplayName("several lines are separated by a newline with none after the last")
	void severalLines_areSeparatedByNewlines() {
		assertEquals("a\nb\nc", join(new ArrayList<>(List.of("a", "b", "c"))));
	}

	@Test
	@DisplayName("one line comes back as it is")
	void oneLine_comesBackUnchanged() {
		assertEquals("only", join(new ArrayList<>(List.of("only"))));
	}

	@Test
	@DisplayName("no lines give an empty string, not the missing-path marker")
	void noLines_giveAnEmptyString() {
		assertEquals("", join(new ArrayList<>()));
	}

	@Test
	@DisplayName("empty lines are kept: two empty lines are just the separator")
	void emptyLines_areKept() {
		assertEquals("\n", join(new ArrayList<>(List.of("", ""))));
		assertEquals("a\n\nb", join(new ArrayList<>(List.of("a", "", "b"))));
	}

	@Test
	@DisplayName("a null line is written as the text null, first, middle and last")
	void nullLine_isWrittenAsText() {
		assertEquals("null\na", join(Arrays.asList(null, "a")));
		assertEquals("a\nnull\nb", join(Arrays.asList("a", null, "b")));
		assertEquals("a\nnull", join(Arrays.asList("a", null)));
	}

	@Test
	@DisplayName("the lines are read from the provider exactly once per call")
	void providerIsReadOnce() {
		join(new ArrayList<>(List.of("a", "b")));

		verify(provider, times(1)).getStringList(Messages.LEVEL_STATS.getPath());
	}
}
