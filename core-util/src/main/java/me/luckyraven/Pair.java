package me.luckyraven;

import java.util.Objects;

public record Pair<P1, P2>(P1 first, P2 second) {

	@Override
	public int hashCode() {
		return Objects.hash(first, second);
	}

}
