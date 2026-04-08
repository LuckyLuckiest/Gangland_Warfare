package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.util.autowire.bean.PostConstruct;

/**
 * Proof-of-concept {@link Configuration} that exercises the bean framework end-to-end without touching any manager
 * already wired by {@code Initializer.events()}. The beans here produce small marker objects whose only job is to
 * verify that:
 * <ul>
 *     <li>{@link Configuration} discovery works against {@code me.luckyraven.config}</li>
 *     <li>configuration constructor injection pulls {@link Gangland} from the container</li>
 *     <li>{@link Bean} methods are invoked in topological order (the second bean depends on the
 *         first via a method parameter, so a wiring bug would surface immediately)</li>
 *     <li>{@link PostConstruct} hooks run after every bean is registered</li>
 * </ul>
 * Migration of real managers from {@code Initializer.events()} into dedicated configuration
 * classes is a separate, follow-up change.
 */
@CustomLog
@Configuration
public class CoreBeansConfig {

	private final Gangland gangland;

	public CoreBeansConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	@Bean
	public BeanWiringMarker beanWiringMarker() {
		return new BeanWiringMarker(gangland.getName());
	}

	@Bean
	public BeanWiringDependent beanWiringDependent(BeanWiringMarker marker) {
		return new BeanWiringDependent(marker);
	}

	@PostConstruct
	public void verifyWired() {
		log.info("CoreBeansConfig wired successfully against plugin {}", gangland.getName());
	}

	/**
	 * Marker bean. Proves a {@link Bean} method can produce a value and have it registered.
	 */
	public record BeanWiringMarker(String pluginName) { }

	/**
	 * Downstream bean that consumes {@link BeanWiringMarker} via parameter injection. Its existence proves topological
	 * ordering: if the marker were instantiated second, the framework would fail before reaching this method.
	 */
	@CustomLog
	public static final class BeanWiringDependent {

		private final BeanWiringMarker marker;

		public BeanWiringDependent(BeanWiringMarker marker) {
			this.marker = marker;
		}

		@PostConstruct
		public void announce() {
			// Bean-level @PostConstruct: BeanFactory's post-construct pass scans every produced
			// bean for @PostConstruct methods, so this proves that path works in addition to the
			// config-level hook above.
			log.info("BeanWiringDependent post-construct ran with marker for plugin {}", marker.pluginName());
		}
	}
}
