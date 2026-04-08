package me.luckyraven.util.autowire.bean;

import lombok.CustomLog;
import me.luckyraven.util.autowire.DependencyContainer;
import me.luckyraven.util.autowire.bean.conditional.ConditionalOnBean;
import me.luckyraven.util.autowire.bean.conditional.ConditionalOnSetting;
import me.luckyraven.util.utilities.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;

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

	private final DependencyContainer container;
	private final JavaPlugin          plugin;
	private final SettingsLookup      settings;
	private final List<Class<?>>      configClasses = new ArrayList<>();

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
		log.info("BeanFactory scanned {}: discovered {} configuration class(es)",
		         basePackage, configClasses.size() - before);
	}

	/**
	 * Instantiate every discovered configuration, evaluate conditions, build the dependency graph and invoke every
	 * {@code @Bean} method in topological order. Throws on missing deps, ambiguous bean references, dependency cycles
	 * or any user-code exception during invocation.
	 */
	public void instantiate() {
		List<Object>         configInstances = new ArrayList<>();
		List<BeanDefinition> definitions     = new ArrayList<>();

		for (Class<?> configClass : configClasses) {
			if (!classConditionsMet(configClass)) {
				log.info("Skipping configuration {}: class-level condition not met", configClass.getName());
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

			for (Method method : configClass.getDeclaredMethods()) {
				if (!method.isAnnotationPresent(Bean.class)) {
					continue;
				}
				if (!methodConditionsMet(method)) {
					log.info("Skipping bean {}.{}: method-level condition not met",
					         configClass.getSimpleName(), method.getName());
					continue;
				}
				definitions.add(buildDefinition(configInstance, method));
			}
		}

		List<BeanDefinition> ordered = BeanGraph.topologicalSort(definitions);

		List<Object> registeredBeans = new ArrayList<>(ordered.size());
		for (BeanDefinition def : ordered) {
			Object bean = invokeBean(def);
			registerBean(def, bean);
			registeredBeans.add(bean);
		}

		runPostConstruct(configInstances);
		runPostConstruct(registeredBeans);

		log.info("Bean wiring complete: {} configs, {} beans", configInstances.size(), ordered.size());
	}

	private BeanDefinition buildDefinition(Object configInstance, Method method) {
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
				beanAnnotation.isGeneric()
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
}
