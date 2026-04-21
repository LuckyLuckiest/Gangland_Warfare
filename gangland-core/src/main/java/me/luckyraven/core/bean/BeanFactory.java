package me.luckyraven.core.bean;

import lombok.CustomLog;
import me.luckyraven.core.bean.autowire.DependencyContainer;
import me.luckyraven.core.bean.conditional.ConditionalOnBean;
import me.luckyraven.core.bean.conditional.ConditionalOnSetting;
import me.luckyraven.core.utilities.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Consumer;

/**
 * Spring-style bean factory built on top of {@link DependencyContainer}. Discovers {@link Configuration}-annotated
 * classes via {@link ReflectionUtil#findClasses(String, ClassLoader)}, instantiates them, collects every
 * {@link Bean}-annotated method into a {@link BeanDefinition}, topologically sorts the definitions via
 * {@link BeanGraph}, invokes each method exactly once and registers the return value as a singleton in the shared
 * container.
 * <p>
 * Conditional beans are evaluated against {@link SettingsLookup} (for {@link ConditionalOnSetting}) and against the
 * live container state (for {@link ConditionalOnBean}). Cycle detection is fail-fast: a {@link BeanCycleException}
 * aborts startup so the user notices the wiring bug immediately.
 * <p>
 * Lifetime contract: every {@code @Bean} method is invoked exactly once. Singletons may be disambiguated by name via
 * {@link Qualifier}. {@code @Bean} methods MUST NOT call other {@code @Bean} methods on the same configuration class
 * via {@code this.foo()} — the framework does not use CGLIB proxies, so direct calls construct duplicate instances.
 * Always declare deps as method parameters.
 */
@CustomLog
public class BeanFactory {

	private final DependencyContainer                container;
	private final JavaPlugin                         plugin;
	private final SettingsLookup                     settings;
	private final List<Class<?>>                     configClasses      = new ArrayList<>();
	private final Map<Phase, Consumer<List<Object>>> phaseHooks         = new EnumMap<>(Phase.class);
	private final List<Object>                       allRegisteredBeans = new ArrayList<>();

	public BeanFactory(DependencyContainer container, JavaPlugin plugin, SettingsLookup settings) {
		this.container = container;
		this.plugin    = plugin;
		this.settings  = settings;
	}

	private static List<?> distinctByIdentity(List<?> instances) {
		IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
		List<Object>                     out  = new ArrayList<>(instances.size());
		for (Object instance : instances) {
			if (instance != null && seen.put(instance, Boolean.TRUE) == null) {
				out.add(instance);
			}
		}
		return out;
	}

	/**
	 * Register a hook that runs immediately after the given phase finishes invoking and registering all of its beans.
	 * The hook receives the list of beans freshly registered in that phase, in topological invocation order. Use this
	 * to bridge bean instantiation with side-effecting subsystems that live outside {@code gangland-core} — e.g. the
	 * gangland-impl bootstrap installs a FILE-phase hook that calls {@code FileManager.initializeAll()} between every
	 * file initializer, and a DATABASE-phase hook that walks {@code RepositoryRegistry.getAllRepositories()} and
	 * publishes each repo back into the container so later @Bean parameters of type {@code IRepository<X>} resolve.
	 *
	 * <p>Hooks are evaluated <i>per bean</i>, not per phase: the hook fires after every individual bean in the phase
	 * is registered, with the cumulative list of phase-so-far. This is what makes staged FILE loading work — Settings
	 * has to finish loading before ScoreboardAddon's bean method runs and reads {@code Settings.getX()} at construction
	 * time.
	 */
	public void setPhaseHook(Phase phase, Consumer<List<Object>> hook) {
		phaseHooks.put(phase, hook);
	}

	/**
	 * Scan the given package for {@link Configuration}-annotated classes. Safe to call multiple times — discovered
	 * classes accumulate.
	 */
	public void scan(String basePackage) {
		Set<Class<?>> found  = ReflectionUtil.findClasses(basePackage, plugin.getClass().getClassLoader());
		int           before = configClasses.size();
		for (Class<?> clazz : found) {
			if (!clazz.isAnnotationPresent(Configuration.class)) {
				continue;
			}
			if (Modifier.isAbstract(clazz.getModifiers()) || clazz.isInterface()) {
				continue;
			}
			configClasses.add(clazz);
		}
		log.debug("BeanFactory scanned {}: discovered {} configuration class(es)",
		          basePackage, configClasses.size() - before);
	}

	/**
	 * Instantiate every discovered configuration and run every bean phase, then run lifecycle callbacks. See
	 * {@link #instantiate(Runnable)} for the full contract — this overload is shorthand for the no-callback case.
	 */
	public void instantiate() {
		instantiate(null);
	}

	/**
	 * Instantiate every discovered configuration, evaluate conditions and run every {@link Phase} in declared order.
	 * Within each phase: build the dependency graph from that phase's beans, topologically sort them, invoke every
	 * {@code @Bean} method, register the result and fire the phase hook (cumulative list of phase-so-far) after each
	 * bean.
	 *
	 * <p>After every bean phase finishes but BEFORE the {@code @PostConstruct} pass runs, the optional
	 * {@code beforeLifecycle} callback is invoked. This allows bridge code to run between bean creation and lifecycle
	 * initialization.
	 *
	 * <p>Then the lifecycle pass runs: {@code @PostConstruct} on every configuration and bean.
	 *
	 * <p>Throws on missing deps, ambiguous bean references, dependency cycles or any user-code exception during
	 * invocation.
	 */
	public void instantiate(Runnable beforeLifecycle) {
		allRegisteredBeans.clear();

		List<Object>                     configInstances = new ArrayList<>();
		Map<Phase, List<BeanDefinition>> phaseDefs       = new EnumMap<>(Phase.class);
		for (Phase phase : Phase.values()) {
			phaseDefs.put(phase, new ArrayList<>());
		}

		for (Class<?> configClass : configClasses) {
			if (!classConditionsMet(configClass)) {
				log.debug("Skipping configuration {}: class-level condition not met", configClass.getName());
				continue;
			}

			Object configInstance;
			try {
				configInstance = container.createInstance(configClass);
			} catch (Exception cause) {
				throw new IllegalStateException(
						"Failed to instantiate @Configuration class " + configClass.getName()
						+ " — check that every constructor parameter is already registered in the container.",
						cause);
			}
			configInstances.add(configInstance);

			Phase classPhase = configClass.getAnnotation(Configuration.class).phase();

			for (Method method : configClass.getDeclaredMethods()) {
				if (!method.isAnnotationPresent(Bean.class)) {
					continue;
				}
				if (!methodConditionsMet(method)) {
					log.debug("Skipping bean {}.{}: method-level condition not met",
					          configClass.getSimpleName(), method.getName());
					continue;
				}
				phaseDefs.get(classPhase).add(buildDefinition(configInstance, method, classPhase));
			}
		}

		for (Phase phase : Phase.values()) {
			List<BeanDefinition> defs = phaseDefs.get(phase);
			if (defs.isEmpty()) {
				continue;
			}

			List<BeanDefinition>   ordered    = BeanGraph.topologicalSort(defs);
			List<Object>           phaseBeans = new ArrayList<>(ordered.size());
			Consumer<List<Object>> hook       = phaseHooks.get(phase);

			for (BeanDefinition def : ordered) {
				Object bean = invokeBean(def);
				registerBean(def, bean);

				// Auto-initialize BeanLifecycle beans immediately after registration, preserving the same
				// timing as the old inline initialize() calls in @Bean methods. This ensures each bean is
				// populated with data before the next bean in topo order is constructed — critical for beans
				// that read from earlier beans during their own construction (e.g. SignManager reads WeaponManager).
				if (bean instanceof BeanLifecycle lifecycle) {
					lifecycle.onInitialize(true);
				}

				phaseBeans.add(bean);
				allRegisteredBeans.add(bean);

				// Fire hook per-bean with the cumulative phase list. The hook is responsible for being idempotent
				// across beans it has already seen — e.g. FileManager.initializeAll() naturally is, since each
				// FileInitializer is registered once and only un-loaded ones execute.
				if (hook != null) {
					hook.accept(phaseBeans);
				}
			}

			log.debug("{} phase complete: {} bean(s)", phase, phaseBeans.size());
		}

		if (beforeLifecycle != null) {
			beforeLifecycle.run();
		}

		runPostConstruct(configInstances);
		runPostConstruct(allRegisteredBeans);
		runPostInitialize(true);

		log.debug("Bean wiring complete: {} configs, {} beans across {} phases",
		          configInstances.size(), allRegisteredBeans.size(), Phase.values().length);
	}

	/**
	 * Runs the full reload lifecycle on all beans implementing {@link BeanLifecycle}. The sequence is:
	 * <ol>
	 *     <li>{@link BeanLifecycle#onPreClear()} in <b>reverse</b> topological order (dependents first)</li>
	 *     <li>{@link BeanLifecycle#onClear()} in <b>reverse</b> topological order</li>
	 *     <li>{@link BeanLifecycle#onInitialize(boolean)} with {@code firstLoad=false} in <b>forward</b> topological
	 *     order (dependencies first)</li>
	 * </ol>
	 *
	 * <p>This method replaces the hand-coded reload sequence in the legacy {@code ReloadPlugin.databaseInitialize()}.
	 */
	public void reloadLifecycleBeans() {
		List<BeanLifecycle> forward  = filterLifecycleBeans();
		List<BeanLifecycle> reversed = new ArrayList<>(forward);
		Collections.reverse(reversed);

		for (BeanLifecycle bean : reversed) {
			bean.onPreClear();
		}

		for (BeanLifecycle bean : reversed) {
			bean.onClear();
		}

		for (BeanLifecycle bean : forward) {
			bean.onInitialize(false);
		}

		runPostInitialize(false);

		log.debug("Lifecycle reload complete: {} bean(s)", forward.size());
	}

	/**
	 * Runs graceful shutdown on all beans implementing {@link BeanLifecycle} in <b>reverse</b> topological order. Call
	 * this during plugin disable, <b>before</b> flushing pending data and closing database connections — shutdown
	 * callbacks may convert active sessions into saveable state (e.g. {@code CarService.destroyAll()}).
	 */
	public void shutdownLifecycleBeans() {
		List<BeanLifecycle> reversed = new ArrayList<>(filterLifecycleBeans());
		Collections.reverse(reversed);

		for (BeanLifecycle bean : reversed) {
			bean.onShutdown();
		}

		log.debug("Lifecycle shutdown complete: {} bean(s)", reversed.size());
	}

	private BeanDefinition buildDefinition(Object configInstance, Method method, Phase phase) {
		Bean   beanAnnotation = method.getAnnotation(Bean.class);
		String name           = beanAnnotation.name().isEmpty() ? method.getName() : beanAnnotation.name();

		List<BeanDefinition.ParameterRequirement> requirements = new ArrayList<>();
		for (Parameter param : method.getParameters()) {
			Qualifier qualifier      = param.getAnnotation(Qualifier.class);
			String    qualifierValue = qualifier != null ? qualifier.value() : null;
			requirements.add(new BeanDefinition.ParameterRequirement(param.getType(), qualifierValue));
		}

		List<Class<?>>    conditionalOnBean = new ArrayList<>();
		ConditionalOnBean condBean          = method.getAnnotation(ConditionalOnBean.class);
		if (condBean != null) {
			conditionalOnBean.addAll(Arrays.asList(condBean.value()));
		}

		List<String>         conditionalOnSetting = new ArrayList<>();
		ConditionalOnSetting condSetting          = method.getAnnotation(ConditionalOnSetting.class);
		if (condSetting != null) {
			conditionalOnSetting.add(condSetting.value());
		}

		return new BeanDefinition(
				method,
				configInstance,
				name,
				method.getReturnType(),
				requirements,
				conditionalOnBean,
				conditionalOnSetting,
				beanAnnotation.publishToServicesManager(),
				beanAnnotation.isGeneric(),
				phase
		);
	}

	private boolean classConditionsMet(Class<?> configClass) {
		ConditionalOnSetting setting = configClass.getAnnotation(ConditionalOnSetting.class);
		if (setting != null && !settings.isEnabled(setting.value())) {
			return false;
		}
		ConditionalOnBean bean = configClass.getAnnotation(ConditionalOnBean.class);
		if (bean != null) {
			for (Class<?> required : bean.value()) {
				if (!container.hasInstance(required)) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean methodConditionsMet(Method method) {
		ConditionalOnSetting setting = method.getAnnotation(ConditionalOnSetting.class);
		if (setting != null && !settings.isEnabled(setting.value())) {
			return false;
		}
		ConditionalOnBean bean = method.getAnnotation(ConditionalOnBean.class);
		if (bean != null) {
			for (Class<?> required : bean.value()) {
				if (!container.hasInstance(required)) {
					return false;
				}
			}
		}
		return true;
	}

	private Object invokeBean(BeanDefinition def) {
		Method      method = def.factoryMethod();
		Parameter[] params = method.getParameters();
		Object[]    args   = new Object[params.length];

		for (int i = 0; i < params.length; i++) {
			BeanDefinition.ParameterRequirement req = def.parameterRequirements().get(i);
			args[i] = resolveParameter(def, params[i], req);
		}

		method.setAccessible(true);
		try {
			return method.invoke(def.configInstance(), args);
		} catch (IllegalAccessException | InvocationTargetException cause) {
			Throwable root = cause instanceof InvocationTargetException ite && ite.getCause() != null
			                 ? ite.getCause() : cause;
			throw new IllegalStateException(
					"Bean " + def.factoryMethod().getDeclaringClass().getSimpleName() + "." + method.getName()
					+ "() threw during invocation",
					root);
		}
	}

	private Object resolveParameter(BeanDefinition def, Parameter param, BeanDefinition.ParameterRequirement req) {
		String beanLabel = def.factoryMethod().getDeclaringClass().getSimpleName()
		                   + "." + def.factoryMethod().getName() + "()";

		if (req.qualifier() != null && !req.qualifier().isEmpty()) {
			Object named = container.getInstance(req.qualifier(), req.type());
			if (named == null) {
				throw new IllegalStateException(
						"Cannot resolve parameter '" + param.getName() + "' of type "
						+ req.type().getSimpleName() + " for bean " + beanLabel
						+ ": no bean named '" + req.qualifier() + "' of that type is registered.");
			}
			return named;
		}

		List<?> candidates = container.getAllInstances(req.type());
		List<?> distinct   = distinctByIdentity(candidates);

		if (distinct.isEmpty()) {
			throw new IllegalStateException(
					"Cannot resolve parameter '" + param.getName() + "' of type "
					+ req.type().getSimpleName() + " for bean " + beanLabel
					+ ": no bean of that type is registered. Add @Qualifier or register a producer.");
		}

		if (distinct.size() > 1) {
			throw new IllegalStateException(
					"Ambiguous bean for parameter '" + param.getName() + "' of type "
					+ req.type().getSimpleName() + " for bean " + beanLabel
					+ ": " + distinct.size() + " candidates registered. Add @Qualifier to disambiguate.");
		}

		return distinct.getFirst();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void registerBean(BeanDefinition def, Object bean) {
		if (bean == null) {
			throw new IllegalStateException(
					"Bean " + def.factoryMethod().getDeclaringClass().getSimpleName() + "."
					+ def.factoryMethod().getName() + "() returned null — bean methods must produce a value.");
		}

		Class returnType = def.returnType();

		// registerInstance(name, type, instance) registers under the raw type AND under the name,
		// so this single call handles both isGeneric=true (consumers must use @Qualifier) and the
		// regular case identically. The isGeneric flag exists today for documentation; a future
		// iteration can use it to forbid raw-class injection when multiple generic beans share a
		// raw type.
		container.registerInstance(def.name(), returnType, bean);

		if (def.publishToServicesManager()) {
			Bukkit.getServicesManager().register(returnType, bean, plugin, ServicePriority.Normal);
		}
	}

	private void runPostConstruct(List<?> targets) {
		Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Object target : targets) {
			if (target == null || !visited.add(target)) {
				continue;
			}
			for (Method method : target.getClass().getDeclaredMethods()) {
				if (!method.isAnnotationPresent(PostConstruct.class)) {
					continue;
				}
				if (method.getParameterCount() != 0) {
					throw new IllegalStateException(
							"@PostConstruct method " + target.getClass().getSimpleName() + "."
							+ method.getName() + "() must take zero parameters.");
				}
				method.setAccessible(true);
				try {
					method.invoke(target);
				} catch (IllegalAccessException | InvocationTargetException cause) {
					Throwable root = cause instanceof InvocationTargetException ite && ite.getCause() != null
					                 ? ite.getCause() : cause;
					throw new IllegalStateException(
							"@PostConstruct " + target.getClass().getSimpleName() + "."
							+ method.getName() + "() threw during invocation",
							root);
				}
			}
		}
	}

	/**
	 * Convention-based lifecycle hook. After every bean is wired and {@code @PostConstruct} has run, walk every bean
	 * and invoke any zero-argument {@code public void initialize()} method declared on the bean's runtime class. Beans
	 * that don't define such a method are skipped silently. This replaces the hand-coded {@code .initialize()} calls
	 * scattered across the legacy {@code Initializer.postInitialize()} (UserManager, RankManager, GangManager,
	 * MemberManager, WeaponManager, SignManager, WeaponLoader, etc.) — each of those managers already exposes a no-arg
	 * {@code initialize()} contract; this hook just calls them automatically.
	 *
	 * <p><b>Skip rule:</b> beans that implement an interface named {@code FileInitializer} are skipped because their
	 * {@code initialize()} method is the file-load hook driven separately by {@code FileManager.initializeAll()} in the
	 * FILE phase post-hook. Calling it again here would re-load the same yaml file. The check uses the interface
	 * <i>name</i> rather than the {@code Class} so {@code gangland-core} doesn't need a dependency on
	 * {@code plugin-persistence}.
	 *
	 * <p>Lookup is cached implicitly via the runtime class because each bean is visited at most once thanks to the
	 * identity-set used by {@link #runPostConstruct}.
	 */
	private void runInitialize(List<?> targets) {
		Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Object target : targets) {
			if (target == null || !visited.add(target)) {
				continue;
			}
			if (implementsInterfaceNamed(target.getClass(), "FileInitializer")) {
				continue;
			}
			if (target instanceof BeanLifecycle) {
				continue;
			}
			Method initialize = findInitializeMethod(target.getClass());
			if (initialize == null) {
				continue;
			}
			initialize.setAccessible(true);
			try {
				initialize.invoke(target);
			} catch (IllegalAccessException | InvocationTargetException cause) {
				Throwable root = cause instanceof InvocationTargetException ite && ite.getCause() != null
				                 ? ite.getCause() : cause;
				throw new IllegalStateException(
						"initialize() on " + target.getClass().getSimpleName() + " threw during invocation",
						root);
			}
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Lifecycle management — reload and shutdown
	// ----------------------------------------------------------------------------------------------------------------

	/**
	 * Reflection-by-name interface check. Walks the class hierarchy looking for any implemented interface whose simple
	 * name matches {@code targetSimpleName}. Used by {@link #runInitialize(List)} to skip {@code FileInitializer} beans
	 * without importing the type.
	 */
	private boolean implementsInterfaceNamed(Class<?> clazz, String targetSimpleName) {
		Class<?> cursor = clazz;
		while (cursor != null && cursor != Object.class) {
			for (Class<?> iface : cursor.getInterfaces()) {
				if (iface.getSimpleName().equals(targetSimpleName)) {
					return true;
				}
				// Walk parent interfaces too — FileInitializer might be inherited via a marker subinterface.
				if (implementsInterfaceNamed(iface, targetSimpleName)) {
					return true;
				}
			}
			cursor = cursor.getSuperclass();
		}
		return false;
	}

	/**
	 * Walk the class hierarchy looking for a public, zero-arg, void-returning method named {@code initialize}. The walk
	 * goes superclass-first so a subclass override is preferred (Java reflection returns the most-specific method
	 * automatically when calling {@code getMethod}, but we use {@code getDeclaredMethods} to also catch package-private
	 * overrides). Returns {@code null} if no such method exists.
	 */
	private Method findInitializeMethod(Class<?> clazz) {
		Class<?> cursor = clazz;
		while (cursor != null && cursor != Object.class) {
			for (Method method : cursor.getDeclaredMethods()) {
				if (!method.getName().equals("initialize")) {
					continue;
				}
				if (method.getParameterCount() != 0) {
					continue;
				}
				if (method.getReturnType() != void.class) {
					continue;
				}
				if (Modifier.isStatic(method.getModifiers())) {
					continue;
				}
				return method;
			}
			cursor = cursor.getSuperclass();
		}
		return null;
	}

	/**
	 * Calls {@link BeanLifecycle#onInitialize(boolean)} on every lifecycle bean in forward topological order. Used both
	 * during first-boot (with {@code firstLoad=true}) and as part of {@link #reloadLifecycleBeans()} (with
	 * {@code firstLoad=false}).
	 */
	private void runLifecycleInitialize(boolean firstLoad) {
		for (BeanLifecycle bean : filterLifecycleBeans()) {
			bean.onInitialize(firstLoad);
		}
	}

	/**
	 * Calls {@link BeanPostInitialize#onPostInitialize(boolean)} on every registered bean implementing that interface,
	 * in forward topological order. Runs <b>after</b> every {@link BeanLifecycle#onInitialize(boolean)} call for the
	 * current pass has completed, so post-init beans see a fully-initialized bean graph even for lifecycle beans they
	 * don't declare a constructor dependency on.
	 *
	 * <p>Example: {@code PlayerBootstrapService} restores wanted levels and fires {@code WantedStartEvent}; that event
	 * drives {@code CopManager.onWantedStart} → {@code startSpawnTask}, which reads {@code CopConfigProvider}. Without
	 * this post-init phase, the event could fire before {@code CopManager.onInitialize(false)} had re-wired
	 * {@code configProvider}, producing an NPE on reload.
	 */
	private void runPostInitialize(boolean firstLoad) {
		Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		int         count   = 0;
		for (Object bean : allRegisteredBeans) {
			if (!(bean instanceof BeanPostInitialize post)) {
				continue;
			}
			if (!visited.add(bean)) {
				continue;
			}
			post.onPostInitialize(firstLoad);
			count++;
		}
		if (count > 0) {
			log.debug("Post-initialize complete: {} bean(s)", count);
		}
	}

	/**
	 * Returns all registered beans that implement {@link BeanLifecycle}, in forward topological order, de-duplicated by
	 * identity. Beans that do not implement the interface are silently excluded.
	 */
	private List<BeanLifecycle> filterLifecycleBeans() {
		List<BeanLifecycle> result  = new ArrayList<>();
		Set<Object>         visited = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Object bean : allRegisteredBeans) {
			if (bean instanceof BeanLifecycle lifecycle && visited.add(bean)) {
				result.add(lifecycle);
			}
		}
		return result;
	}
}
