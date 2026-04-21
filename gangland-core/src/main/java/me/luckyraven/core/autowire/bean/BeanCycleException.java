package me.luckyraven.core.autowire.bean;

import java.util.List;

/**
 * Thrown when {@link BeanGraph} detects a dependency cycle between {@link Bean} methods. The exception message lists
 * every node in the cycle so the user can immediately identify which beans are mutually dependent.
 */
public class BeanCycleException extends RuntimeException {

	public BeanCycleException(List<String> cyclePath) {
		super(buildMessage(cyclePath));
	}

	private static String buildMessage(List<String> cyclePath) {
		String joined = String.join(" -> ", cyclePath);
		return "Bean dependency cycle detected: " + joined;
	}
}
