package me.luckyraven.datastructure;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class Node<T> implements Cloneable {

	@Getter
	private final List<Node<T>> children;

	@Getter
	private T       data;
	@Getter
	@Setter
	private Node<T> parent;

	public Node(T data) {
		this.data = data;
		this.children = new ArrayList<>();
	}

	public Node(T data, List<Node<T>> children) {
		this(data);
		addAll(children);
	}

	public void add(Node<T> child) {
		child.setParent(this);
		children.add(child);
	}

	public void add(int index, Node<T> child) {
		child.setParent(this);
		children.add(index, child);
	}

	public void addAll(List<Node<T>> children) {
		for (Node<T> child : children)
			child.setParent(this);
		this.children.addAll(children);
	}

	public boolean remove(Node<T> child) {
		return children.remove(child);
	}

	@Override
	public String toString() {
		return String.format("[data=%s,parent=%s]", data, parent == null ? "null" : parent.getData());
	}

	@Override
	public Node<T> clone() {
		try {
			return (Node<T>) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new RuntimeException(exception);
		}
	}

}
