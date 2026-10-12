package org.luckyraven.gangland.gang.database.repositories.gang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.gangland.gang.Gang;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GR-20: a SQL NULL text column must load as the fallback, never as the literal string {@code "null"}.
 */
@DisplayName("GangRepository - NULL-safe row text")
class GangRepositoryRowTest {

	@Test
	@DisplayName("text(null, fallback) returns the fallback, not the string \"null\"")
	void text_null_returnsFallback() {
		assertEquals("", GangRepository.text(null, ""));
	}

	@ParameterizedTest(name = "state text {0} reads as INVITE")
	@NullSource
	@ValueSource(strings = {"null", "CLOSE", "garbage", "INVITE"})
	@DisplayName("D7: NULL, CLOSE, unknown and INVITE rows all read as INVITE, never OPEN")
	void parseState_everythingButOpenIsInvite(String raw) {
		assertEquals(Gang.State.INVITE, GangRepository.parseState(raw));
	}

	@Test
	@DisplayName("D7: a stored OPEN stays OPEN")
	void parseState_open() {
		assertEquals(Gang.State.OPEN, GangRepository.parseState("OPEN"));
	}

	@Test
	@DisplayName("text(value, fallback) returns the value as a string")
	void text_value_returnsString() {
		assertEquals("Hood", GangRepository.text("Hood", ""));
	}

}
