package org.luckyraven.gangland.file.configuration;

import org.luckyraven.gangland.file.LanguageLoader;

import java.util.List;

/**
 * Lightweight abstraction over the underlying language YAML loaded by {@link LanguageLoader}.
 *
 * <p>Phase 2 (sub-task 2.4) replaced the static {@code Messages.setMessageConfiguration(YamlConfiguration)} seam with
 * {@code Messages.init(MessageProvider)}: the provider is now a proper bean that any class can inject for typed
 * lookups, while the {@link Messages} enum keeps its 474 existing call sites untouched. The single static seam
 * remaining is {@code Messages.init(...)}, called once at startup from {@code LanguageLoader.onInitialize(...)} /
 * {@code FileConfig.languageLoader(...)}.
 */
public interface MessageProvider {

	String getString(String path);

	List<String> getStringList(String path);

}
