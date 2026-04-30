package org.luckyraven.gangland.data.teleportation;

import org.luckyraven.gangland.exception.PluginException;

public class IllegalTeleportException extends PluginException {

	public IllegalTeleportException() {
		super();
	}

	public IllegalTeleportException(String message) {
		super(message);
	}

}
