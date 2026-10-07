<!-- Research sub-agent report (2026-09-08, sonnet) — for planner P2 (Bartizan bootstrap). Oriel is Gradle; the Java shapes transfer, the build files do not. -->

# Oriel as the copy template for a standalone Keystone-consumer plugin

Repo `E:\Programming\java\Oriel` (Gradle Kotlin DSL, Java 17, Keystone `1.7.0` from mavenLocal filtered to `includeGroup("org.luckyraven")`). Shipping module `menu-plugin`.

## 1. Plugin main class — `menu-plugin/src/main/java/org/luckyraven/oriel/plugin/OrielPlugin.java`

`@CustomLog public final class OrielPlugin extends JavaPlugin` (`:28-29`); `MASTER_COMMAND = "menu"` (`:31`); `private static OrielPlugin instance` + `@Getter private OrielContext context` (`:33-36`); no `onLoad()`.

`onEnable` (`:133-150`): `instance = this; context = new OrielContext(this); context.bootstrap(); PermissionManager pm = context.get(PermissionManager.class); if (pm != null) pm.addPermission("command.main"); registerServices();` all inside `try { … } catch (Throwable t) { log.warn("Oriel failed to enable", t); disablePlugin(this); }`. (`"command.main"` is un-prefixed; `PermissionManager` prefixes with the lower-cased plugin name.)

`onDisable` (`:152-170`): `getServer().getServicesManager().unregisterAll(this)`; null out every static slot the plugin installed (`ActionParser.setActionRegistry(null)`, `ItemDefinitionLoader.set…(null)` ×3, `PacketBridge.reset()`); `context.shutdownBeans()` in try/catch with `container.clear()` in finally; `instance = null`. Static `lookup(Class<T>)` helper (`:42-131`) returns `plugin.context.get(type)` or null.

## 2. `OrielContext` — `…/plugin/bootstrap/OrielContext.java` (161 lines)

Constants `:31-33`: `CONFIG_PACKAGE = "org.luckyraven.oriel.plugin.config"`, `LISTENER_PACKAGE = "org.luckyraven.oriel"`, `COMMAND_PACKAGE = "org.luckyraven.oriel"`. Fields `:35-43`: `DependencyContainer container`, `BeanFactory beanFactory`, `OrielPlugin plugin`, `PluginCommand masterCommand`.

Constructor `:45-67`: `container = new DependencyContainer()`; `SettingsLookup settings = key -> false`; `beanFactory = new BeanFactory(container, plugin, settings)`; `beanFactory.setConventionInitializeEnabled(false)` (Oriel drives `initialize()` manually — Gangland/Bartizan keep the convention pass); `registerInstance` for `OrielContext`, `DependencyContainer`, `JavaPlugin`, `OrielPlugin`, `SettingsLookup`, `BeanFactory` (six calls, that order). No `ModuleLoader`.

`bootstrap()` `:119-125`: `beanFactory.scan(CONFIG_PACKAGE); beanFactory.instantiate(); runListenerPhase(); runCommandPhase();` — zero `setPhaseHook`, zero `registerListenerScan/registerCommandScan`; Oriel runs those two phases manually. `runListenerPhase` `:127-131`: `DefaultListenerService ls = requireBean(...); ls.scanAndRegisterListeners(LISTENER_PACKAGE, plugin); ls.registerEvents();`. `runCommandPhase` `:133-151`: `CommandManager cm = requireBean(...)`; `plugin.getCommand(MASTER_COMMAND)` (warn + return when missing); `command.setExecutor(cm)`; `cm.scanAndRegisterCommands(COMMAND_PACKAGE, plugin.getClass().getClassLoader())`; `command.setTabCompleter(new CommandTabCompleter(CommandManager.getCommands()))`; `BrigadierTabRegistrar.registerIfSupported(plugin, command, CommandManager.getCommands())`; store `masterCommand`. `requireBean` `:153-160` throws `IllegalStateException("<Type> bean missing — add a @Bean method …")`. `reloadBeans()` `:81` → `beanFactory.reloadLifecycleBeans()` + `refreshTabCompletion()` (re-register Brigadier + `player.updateCommands()`); `shutdownBeans()` → `beanFactory.shutdownLifecycleBeans()`.

Companion `…/bootstrap/DefaultListenerService.java` (25 lines): `extends ListenerService`, ctor `(JavaPlugin, DependencyContainer, SettingsLookup)`, `invokeMethod(String condition)` → `settings.isEnabled(condition)` — Keystone ships only the abstract base, so every consumer writes this. `…/bootstrap/UpdateCheckBootstrap.java`: `BeanPostInitialize` → `if (firstLoad) updateChecker.checkAtStartup()`.

## 3. `KernelConfig` and `WiringConfig` (`…/plugin/config/`)

`WiringConfig` (`@Configuration`, default CONFIG, ctor `(OrielPlugin)`): `@Bean DefaultListenerService listenerService(DependencyContainer, SettingsLookup)`; `@Bean CommandManager commandManager(DependencyContainer, SettingsLookup)` → `new CommandManager(plugin, container, settings, plugin.getName().toLowerCase(), MASTER_COMMAND)`.

`KernelConfig` (`@Configuration(phase = Phase.KERNEL)`, ctor `(OrielPlugin)` with `pluginName = plugin.getName().toLowerCase()`; javadoc `:36-39`: only the pre-registered instances can be injected here). Minimal Keystone-only subset: `loggingSink()`, `recentFaultsSink()`, `diagnostics(LoggingSink, RecentFaultsSink)` → `Diagnostics.withDefaults()` + `addSink` ×2 + **`Diagnostics.install(diagnostics)`**; `permissionWorker()` → `new PermissionWorker(pluginName)`; `permissionManager(PermissionHandler)` → `new PermissionManager(handler, pluginName)` (resolves because `PermissionWorker implements PermissionHandler`); `updateChecker(PermissionManager)` → `new UpdateChecker(plugin, pm, pluginName, 0 /* spigot id */)`; `updateCheckBootstrap(UpdateChecker)`. No logger bean (Lombok `@CustomLog`). Oriel's other 20 kernel beans are menu registries.

What a minimal consumer must produce: Diagnostics (KERNEL), PermissionWorker + PermissionManager (KERNEL, needed by `CommandManager`), FileManager (only if loading YAML; Oriel builds it in CONFIG, Gangland in KERNEL), CommandManager + a ListenerService impl (CONFIG), settings/messages (FILE phase — see §7), `ChatUtil` static, optional `CooldownService`, `PlaceholderProvider`. `Phase` enum: `KERNEL, FILE, DATABASE, CONFIG, LIFECYCLE, LISTENER, COMMAND`. Oriel's 13 configurations: `KernelConfig` KERNEL; `PersistenceConfig`, `DemoComponentsConfig`, `DemoItemProvidersConfig` DATABASE; `AnimationConfig` LIFECYCLE (installs `PacketAdapter`); the rest CONFIG. The "seed bean" idiom (unused `@Bean` parameter purely for ordering) appears at `ConfigLoaderConfig:98-104` and `PersistenceConfig:47-52`.

## 4. `plugin.yml` (`menu-plugin/src/main/resources/plugin.yml`)

`name: Oriel`, `version: ${version}` (Gradle `processResources` expand; Maven = filtered resources + `${project.version}`), `main: org.luckyraven.oriel.plugin.OrielPlugin`, `api-version: '1.16'`, `authors: [LuckyRaven10]`, `depend: [Keystone]`, `softdepend: [PlaceholderAPI, NBTAPI, Vault]`, `commands: menu: {description, aliases: [oriel, orielmenu], permission: oriel.command.main, permission-message}`.

## 5. `module.properties`

`menu-plugin/src/main/resources/org/luckyraven/oriel/module.properties` = `module.name=Oriel` (Keystone's Logger walks the package path → prefix `[Oriel.ClassName]`). Needs `lombok.config` (`lombok.log.custom.declaration = org.apache.logging.log4j.Logger org.luckyraven.keystone.logging.Logger.getLogger(TYPE)`, `config.stopBubbling = true`, `lombok.log.fieldName = log`) + `@CustomLog` + `log4j-api` compileOnly/provided.

## 6. Build files

Gradle only (`build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `menu-plugin/build.gradle.kts`). Keystone deps `compileOnly` + `testImplementation` twins (Maven `provided` covers both). Shadow: `net.wesjd.anvilgui` and `com.cryptomorin.xseries` relocated, Keystone never shaded, `minimize()` off. `menu-archetype/src/main/resources/archetype-resources/pom.xml` is a liftable minimal Maven consumer POM (spigot-api provided, `maven.compiler.release` 17, filtered resources, compiler 3.13.0).

## 7. Settings and messages

Oriel ships only Bukkit `config.yml` through `OrielSettings` (`plugin.saveDefaultConfig(); reloadConfig()`; live reads) and hardcodes every message via `ChatUtil.color(...)` — **no `settings.yml`, no `message/message_en.yml`, no `LanguageLoader`**. The Keystone recipe Bartizan should use instead (from Keystone's own consumer-scenario fixtures `keystone-plugin/src/test/java/org/luckyraven/keystone/scenario/fixture/files/{FilesConfig, SettingsLoader}.java` and `BeanBootstrapScenarioTest.java:112-134`):

```java
@Configuration(phase = Phase.FILE)
public class FilesConfig {
	@Bean public SettingsLoader settingsLoader(JavaPlugin p, FileManager fm) throws IOException { ... }   // FileInitializer over FileHandler(plugin, "settings", "yml")
	@Bean public LanguageLoader languageLoader(JavaPlugin p, FileManager fm, SettingsLoader settings) {
		LanguageLoader loader = new LanguageLoader(p, fm, settings::language, "message", "message",
		                                           BartizanMessages::findMissingPaths, BartizanMessages::install);
		loader.initialize();      // onInitialize(firstLoad=true) is a no-op; the bean initialises once itself
		return loader;
	}
}
```
plus `beanFactory.setPhaseHook(Phase.FILE, beans -> { for (Object b : beans) if (b instanceof FileInitializer i) fileManager.registerInitializer(i); fileManager.initializeAll(); })` and `FileManager` registered as a KERNEL bean/instance. `SettingsLoader` pattern: `implements FileInitializer`, `new FileHandler(plugin, "settings", "yml")`, `fileManager.addFile(handler, true)`, `initialize()` reads through `FileHandlerReader.read(handler, new ConfigReport())` → `reader.get("Key").asString().orElse(...)`.

## 8. Commands

No `commands.json` in Oriel; no `CommandManager` subclass. Sub-commands are `@CommandHandler public final class XCommand extends org.luckyraven.keystone.command.Command`, ctor `super(plugin, "reload", false /* playerOnly */)`, override `onExecute(Argument, CommandSender, String[])`, `initializeArguments()`, `help(CommandSender, int)`; constructor-injected from the container (`OpenCommand(JavaPlugin, MenuRegistry)`). Seven leaves in `…/plugin/command/`.

## Minimal file checklist for the new repo

`pom.xml` (keystone-* provided, shade with Keystone excluded) · `lombok.config` · `Bartizan.java` (`@CustomLog extends JavaPlugin`, onEnable/onDisable as §1) · `bootstrap/BartizanContext.java` (container + factory + `bootstrap()` with the FILE phase hook) · `bootstrap/DefaultListenerService.java` · `config/KernelConfig.java` (KERNEL: Diagnostics, permissions, FileManager, PacketBridge.install) · `config/FilesConfig.java` (FILE) · `config/DatabaseConfig.java` (DATABASE) · `config/WiringConfig.java` (CONFIG: ListenerService, CommandManager) · `command/*.java` · `src/main/resources/plugin.yml` · `src/main/resources/org/luckyraven/bartizan/module.properties` · `settings.yml` + `message/message_en.yml` + `weapon/*.yml` + `items/*.yml` + `commands.json`.
