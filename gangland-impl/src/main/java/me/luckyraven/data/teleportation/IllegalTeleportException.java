package me.luckyraven.data.teleportation;

import me.luckyraven.exception.PluginException;

public class IllegalTeleportException extends PluginException {

	public IllegalTeleportException() {
		super();
	}

	public IllegalTeleportException(String message) {
		super(message);
	}

}
