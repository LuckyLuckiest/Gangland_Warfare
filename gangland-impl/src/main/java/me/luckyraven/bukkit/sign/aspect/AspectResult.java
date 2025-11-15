package me.luckyraven.bukkit.sign.aspect;

import lombok.Getter;

@Getter
public class AspectResult {

	private final boolean success;
	private final String  message;
	private final boolean continueExecution;

	public AspectResult(boolean success, String message, boolean continueExecution) {
		this.success           = success;
		this.message           = message;
		this.continueExecution = continueExecution;
	}

	public static AspectResult success(String message) {
		return new AspectResult(true, message, true);
	}

	public static AspectResult failure(String message) {
		return new AspectResult(false, message, false);
	}

	public static AspectResult successContinue(String message) {
		return new AspectResult(true, message, true);
	}

	public static AspectResult successStop(String message) {
		return new AspectResult(true, message, false);
	}

}
