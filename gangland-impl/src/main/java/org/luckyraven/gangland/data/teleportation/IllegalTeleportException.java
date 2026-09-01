package org.luckyraven.gangland.data.teleportation;

import org.luckyraven.keystone.exception.PluginException;

public class IllegalTeleportException extends PluginException {

	public IllegalTeleportException() {
		super();
	}

	public IllegalTeleportException(String message) {
		super(message);
	}

}
