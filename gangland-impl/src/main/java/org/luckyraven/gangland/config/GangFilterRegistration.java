package org.luckyraven.gangland.config;

import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.gangland.gang.GangFilterAdapter;
import org.luckyraven.gangland.gang.member.MemberFilterAdapter;
import org.luckyraven.gangland.inventory.filter.FilterBinding;
import org.luckyraven.gangland.inventory.filter.FilterRegistry;
import org.luckyraven.gangland.inventory.filter.SortDescriptor;
import org.luckyraven.gangland.inventory.filter.StandardFilterField;

import java.util.List;

/**
 * Registers the filter bindings owned by gangland-impl's list views. Each binding declares which
 * {@link StandardFilterField}s the view exposes plus the sort cycle used by the "Sort" button.
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

	@Bean
	public GangFilterAdapter gangFilterAdapter() {
		return new GangFilterAdapter();
	}

	@Bean
	public MemberFilterAdapter memberFilterAdapter() {
		return new MemberFilterAdapter();
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
