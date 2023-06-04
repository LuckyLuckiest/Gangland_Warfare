package me.luckyraven.exception;

public class PluginException extends RuntimeException {

	private final int token;

	public PluginException() {
		super();
		this.token = 0;
	}

	public PluginException(int token) {
		this.token = token;
	}

	public PluginException(String message) {
		super(message);
		this.token = 0;
	}

	public PluginException(String message, int token) {
		super(message);
		this.token = token;
	}

	public int getToken() {
		return token;
	}

}
