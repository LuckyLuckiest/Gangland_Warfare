package org.luckyraven.gangland.gang.database.repositories.gang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

	@Test
	@DisplayName("text(value, fallback) returns the value as a string")
	void text_value_returnsString() {
		assertEquals("Hood", GangRepository.text("Hood", ""));
	}

}
