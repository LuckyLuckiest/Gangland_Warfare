package me.luckyraven.command.argument;

import lombok.Getter;

@Getter
public class ArgumentResult<T extends Argument> {

	private final ResultState state;
	private final T           argument;

	private ArgumentResult(ResultState state, T argument) {
		this.state = state;
		this.argument = argument;
	}

	public static <T extends Argument> ArgumentResult<T> success(T argumnet) {
		return new ArgumentResult<>(ResultState.SUCCESS, argumnet);
	}

	public static <T extends Argument> ArgumentResult<T> noPermission(T argument) {
		return new ArgumentResult<>(ResultState.NO_PERMISSION, argument);
	}

	public static <T extends Argument> ArgumentResult<T> notFound() {
		return new ArgumentResult<>(ResultState.NOT_FOUND, null);
	}

	public enum ResultState {
		SUCCESS,
		NO_PERMISSION,
		NOT_FOUND
	}

}
