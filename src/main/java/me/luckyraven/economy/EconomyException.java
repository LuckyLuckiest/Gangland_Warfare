package me.luckyraven.economy;

import me.luckyraven.exception.PluginException;

public class EconomyException extends PluginException {

	public EconomyException() {
		super();
	}

	public EconomyException(int token) {
		super(token);
	}

	public EconomyException(String message) {
		super(message);
	}

	public EconomyException(String message, int token) {
		super(message, token);
	}

}
