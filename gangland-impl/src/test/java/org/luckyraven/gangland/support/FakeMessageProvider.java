package org.luckyraven.gangland.support;

import org.luckyraven.keystone.message.MessageProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal {@link MessageProvider} recording exactly the paths a test wires in. An unregistered scalar path returns
 * {@code null} (reaching {@code Messages.toString()}'s {@code "<missing: ...>"} branch); an unregistered list path
 * returns an empty list rather than {@code null} — {@code Messages} never distinguishes "missing" from "empty" for
 * list-typed constants, since {@code convertFromList} always returns a (possibly empty) string, never {@code null}.
 */
public final class FakeMessageProvider implements MessageProvider {

	private final Map<String, String>       strings = new LinkedHashMap<>();
	private final Map<String, List<String>> lists   = new LinkedHashMap<>();

	public FakeMessageProvider withString(String path, String value) {
		strings.put(path, value);
		return this;
	}

	public FakeMessageProvider withList(String path, List<String> value) {
		lists.put(path, value);
		return this;
	}

	@Override
	public String getString(String path) {
		return strings.get(path);
	}

	@Override
	public List<String> getStringList(String path) {
		return lists.getOrDefault(path, List.of());
	}
}
