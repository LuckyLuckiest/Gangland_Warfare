package me.luckyraven.sign.validation;

public class SignValidationException extends Exception {

	private final int    lineNumber;
	private final String lineContent;

	public SignValidationException(String message, int lineNumber, String lineContent) {
		super(message + " (Line " + (lineNumber + 1) + ": '" + lineContent + "')");

		this.lineNumber  = lineNumber;
		this.lineContent = lineContent;
	}

	public SignValidationException(String message) {
		super(message);

		this.lineNumber  = -1;
		this.lineContent = "";
	}
}
