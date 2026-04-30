package org.luckyraven.gangland.persistence.config.dsl;

import org.luckyraven.gangland.persistence.config.NodeReader;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link Map} decorator that records every key looked up via {@link #get}, {@link #containsKey}, or full iteration via
 * {@link #entrySet}. Used by DSL adapters to detect attributes the admin typed but the converter never read, mirroring
 * the YAML touched-key tracking in {@link NodeReader}.
 *
 * <p>Iteration is treated as "reads every current key" — a converter that walks
 * {@code entrySet()} asserts it consumed the whole attribute set, so no attribute should be flagged unknown afterward.
 */
public final class TrackingStringMap extends AbstractMap<String, String> {

	private final Map<String, String> delegate;
	private final Set<String>         touched;

	public TrackingStringMap(Map<String, String> delegate, Set<String> touched) {
		this.delegate = delegate;
		this.touched  = touched;
	}

	@Override
	public String get(Object key) {
		if (key instanceof String s) touched.add(s);
		return delegate.get(key);
	}

	@Override
	public boolean containsKey(Object key) {
		if (key instanceof String s) touched.add(s);
		return delegate.containsKey(key);
	}

	@Override
	public Set<Entry<String, String>> entrySet() {
		touched.addAll(delegate.keySet());
		return delegate.entrySet();
	}

	@Override
	public Set<String> keySet() {
		touched.addAll(delegate.keySet());
		return delegate.keySet();
	}

	@Override
	public int size() {
		return delegate.size();
	}

	@Override
	public boolean isEmpty() {
		return delegate.isEmpty();
	}

}
