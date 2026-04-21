package me.luckyraven.core.bean;

import lombok.CustomLog;

import java.util.*;

/**
 * Builds a dependency DAG from a flat list of {@link BeanDefinition}s and produces a topological ordering. Each bean's
 * parameter list becomes incoming edges from the beans that produce the matching type / name. Parameters that resolve
 * to instances already present in the dependency container do not contribute edges — they will simply be looked up at
 * invocation time.
 * <p>
 * On cycle detection, throws {@link BeanCycleException} with a human-readable cycle path.
 */
@CustomLog
public final class BeanGraph {

	private BeanGraph() {
	}

	/**
	 * Sort the given definitions topologically. Definitions whose deps are all satisfied externally (i.e. resolved from
	 * the container, not from another bean in the list) come first; downstream beans follow.
	 */
	public static List<BeanDefinition> topologicalSort(List<BeanDefinition> definitions) {
		Map<BeanDefinition, Set<BeanDefinition>> incoming = new HashMap<>();
		Map<BeanDefinition, Set<BeanDefinition>> outgoing = new HashMap<>();

		for (BeanDefinition def : definitions) {
			incoming.put(def, new LinkedHashSet<>());
			outgoing.put(def, new LinkedHashSet<>());
		}

		// Index by name for qualifier lookup, and by raw type for fallback resolution.
		Map<String, BeanDefinition>         byName = new HashMap<>();
		Map<Class<?>, List<BeanDefinition>> byType = new HashMap<>();

		for (BeanDefinition def : definitions) {
			byName.put(def.name(), def);
			byType.computeIfAbsent(def.returnType(), k -> new ArrayList<>()).add(def);
		}

		for (BeanDefinition consumer : definitions) {
			for (BeanDefinition.ParameterRequirement req : consumer.parameterRequirements()) {
				BeanDefinition producer = resolveProducer(req, byName, byType);
				if (producer != null && producer != consumer) {
					outgoing.get(producer).add(consumer);
					incoming.get(consumer).add(producer);
				}
			}
		}

		// Kahn's algorithm.
		Deque<BeanDefinition> ready = new ArrayDeque<>();
		for (BeanDefinition def : definitions) {
			if (incoming.get(def).isEmpty()) {
				ready.add(def);
			}
		}

		List<BeanDefinition> ordered = new ArrayList<>(definitions.size());
		while (!ready.isEmpty()) {
			BeanDefinition next = ready.poll();
			ordered.add(next);
			for (BeanDefinition downstream : outgoing.get(next)) {
				Set<BeanDefinition> remaining = incoming.get(downstream);
				remaining.remove(next);
				if (remaining.isEmpty()) {
					ready.add(downstream);
				}
			}
		}

		if (ordered.size() != definitions.size()) {
			throw new BeanCycleException(findCyclePath(definitions, incoming, ordered));
		}

		return ordered;
	}

	private static BeanDefinition resolveProducer(
			BeanDefinition.ParameterRequirement req,
			Map<String, BeanDefinition> byName,
			Map<Class<?>, List<BeanDefinition>> byType
	) {
		if (req.qualifier() != null && !req.qualifier().isEmpty()) {
			return byName.get(req.qualifier());
		}

		List<BeanDefinition> exact = byType.get(req.type());
		if (exact != null && exact.size() == 1) {
			return exact.getFirst();
		}

		// Walk every definition once for assignability — covers interface-typed params consuming
		// concrete-typed bean methods. Multi-candidate ambiguity is left to BeanFactory to flag at
		// invocation time, since cycle detection only needs *one* edge per dep to draw the graph.
		BeanDefinition assignable = null;
		for (List<BeanDefinition> bucket : byType.values()) {
			for (BeanDefinition candidate : bucket) {
				if (req.type().isAssignableFrom(candidate.returnType())) {
					if (assignable != null) {
						return null;
					}
					assignable = candidate;
				}
			}
		}
		return assignable;
	}

	private static List<String> findCyclePath(
			List<BeanDefinition> definitions,
			Map<BeanDefinition, Set<BeanDefinition>> incoming,
			List<BeanDefinition> alreadyOrdered
	) {
		Set<BeanDefinition>  orderedSet = new HashSet<>(alreadyOrdered);
		List<BeanDefinition> stuck      = new ArrayList<>();
		for (BeanDefinition def : definitions) {
			if (!orderedSet.contains(def)) {
				stuck.add(def);
			}
		}

		// DFS from the first stuck node, following an arbitrary unsatisfied incoming edge until we
		// revisit a node — that's the cycle.
		BeanDefinition       start    = stuck.getFirst();
		List<BeanDefinition> path     = new ArrayList<>();
		Set<BeanDefinition>  visiting = new LinkedHashSet<>();
		BeanDefinition       cursor   = start;
		while (cursor != null) {
			if (visiting.contains(cursor)) {
				List<String> result  = new ArrayList<>();
				boolean      started = false;
				for (BeanDefinition node : visiting) {
					if (node == cursor) {
						started = true;
					}
					if (started) {
						result.add(node.name());
					}
				}
				result.add(cursor.name());
				return result;
			}
			visiting.add(cursor);
			path.add(cursor);

			BeanDefinition nextHop = null;
			for (BeanDefinition upstream : incoming.get(cursor)) {
				if (!orderedSet.contains(upstream)) {
					nextHop = upstream;
					break;
				}
			}
			cursor = nextHop;
		}

		// Defensive fallback: report every stuck node so the user still sees something useful.
		List<String> fallback = new ArrayList<>();
		for (BeanDefinition def : stuck) {
			fallback.add(def.name());
		}
		return fallback;
	}
}
