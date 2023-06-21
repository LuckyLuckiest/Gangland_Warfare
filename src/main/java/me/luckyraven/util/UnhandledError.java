package me.luckyraven.util;

import lombok.Getter;

public enum UnhandledError {

	SQL_ERROR("sql"), FILE_LOADER_ERROR("files loader"), COMMANDS_ERROR("commands"), MISSING_JAR_ERROR(
			"missing jar file"), HELP_ERROR("help");

	private final @Getter String message;

	UnhandledError(String message) {
		this.message = "Unhandled error (" + message + ")";
	}

}
