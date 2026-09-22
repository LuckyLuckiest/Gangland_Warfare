package org.luckyraven.gangland.config;

import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.gangland.menu.filter.FilterBinding;
import org.luckyraven.gangland.menu.filter.FilterRegistry;
import org.luckyraven.gangland.menu.filter.SortDescriptor;
import org.luckyraven.gangland.menu.filter.StandardFilterField;

import java.util.List;

/**
 * Registers the filter bindings the gang list views use. Each binding declares which {@link StandardFilterField}s
 * the view exposes plus the sort cycle used by the "Sort" button — pure metadata (enum values, plain strings), no
 * {@code Gang}/{@code Member} type involved, so this stays in gangland-impl even though the module owns the data
 * being filtered (W54 F1: {@code FilterBinding} carries no entity type — only the {@code FilterAdapter<Gang>}/
 * {@code FilterAdapter<Member>} projections that turn a binding into filtered results need to live where
 * {@code Gang}/{@code Member} are visible, which is why {@code GangFilterAdapter}/{@code MemberFilterAdapter}
 * moved into the gang module while this registration did not). The binding ids ({@code "gangs"},
 * {@code "gang_members"}) are duplicated as literals in the module's {@code GangMenuItemSourceContribution} —
 * not shared via a constant, since sharing one would need a new cross-boundary reference for two strings that are
 * already hardcoded switch labels on the impl side of the now-deleted {@code GangItemSourceProvider} this replaces.
 *
 * <p>Deleted in the original W51 gate on the (wrong) assumption that this needed a module type; restored unchanged
 * in the W54 fix round once the actual composition was checked — see the gate report.
 *
 * <p>Takes {@link GanglandContext} in the constructor (KERNEL-phase, always available) and resolves the
 * {@link FilterRegistry} lazily inside {@link #register()} — the registry is defined by {@link GameplayConfig} in the
 * same CONFIG phase, so it is not yet in the container when this {@code @Configuration} class is instantiated.
 */
@Configuration
public class GangFilterRegistration {

	public static final String BINDING_GANGS        = "gangs";
	public static final String BINDING_GANG_MEMBERS = "gang_members";

	private final GanglandContext context;

	public GangFilterRegistration(GanglandContext context) {
		this.context = context;
	}

	@PostConstruct
	public void register() {
		FilterRegistry registry = context.get(FilterRegistry.class);
		if (registry == null) return;

		registry.register(new FilterBinding(
				BINDING_GANGS,
				"phone_gang_search",
				List.of(StandardFilterField.NAME,
				        StandardFilterField.DESCRIPTION,
				        StandardFilterField.COLOR,
				        StandardFilterField.MEMBERS,
				        StandardFilterField.DATE),
				SortDescriptor.asc(StandardFilterField.NAME),
				List.of(SortDescriptor.asc(StandardFilterField.NAME),
				        SortDescriptor.desc(StandardFilterField.MEMBERS),
				        SortDescriptor.desc(StandardFilterField.DATE))));

		registry.register(new FilterBinding(
				BINDING_GANG_MEMBERS,
				"user_stat",
				List.of(StandardFilterField.NAME,
				        StandardFilterField.CATEGORY,
				        StandardFilterField.MEMBERS,
				        StandardFilterField.DATE),
				SortDescriptor.asc(StandardFilterField.NAME),
				List.of(SortDescriptor.asc(StandardFilterField.NAME),
				        SortDescriptor.asc(StandardFilterField.CATEGORY),
				        SortDescriptor.desc(StandardFilterField.MEMBERS),
				        SortDescriptor.desc(StandardFilterField.DATE))));
	}

}
