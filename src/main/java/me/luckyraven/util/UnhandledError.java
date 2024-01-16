package me.luckyraven.util;

public enum UnhandledError {


	FILE_LOADER_ERROR("files loader"),
	FILE_CREATE_ERROR("file create"),
	FILE_SAVE_ERROR("file save"),
	FILE_EDIT_ERROR("file edit"),
	MISSING_JAR_ERROR("missing jar file"),
	SQL_ERROR("sql"),
	COMMANDS_ERROR("commands"),
	HELP_ERROR("help"),
	ERROR("unchecked"),
	;

	private final String message;

	UnhandledError(String message) {
		this.message = "Unhandled error (" + message + ")";
	}

	@Override
	public String toString() {
		return message;
	}
}
