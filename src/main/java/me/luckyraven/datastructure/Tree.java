package me.luckyraven.datastructure;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.exception.PluginException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

@Getter
@Setter
public class Tree<E> implements Iterable<E> {

	private Node<E> root;

	public Tree() {
		this.root = null;
	}

	public void add(@NotNull E obj) {
		Node<E> newNode = new Node<>(obj);
		if (root == null) root = newNode;
		else root.add(newNode);
	}

	public void add(Node<E> node) {
		if (root == null) root = node;
		else root.add(node);
	}

	public boolean isEmpty() {
		return root == null;
	}

	public boolean contains(E key) {
		if (root == null) return false;
		return findNode(root, key) != null;
	}

	public int getNumberOfDescendants(Node<E> root) {
		int size = root.getChildren().size();
		for (Node<E> child : root.getChildren()) size += getNumberOfDescendants(child);
		return size;
	}

	public int size() {
		return getNumberOfDescendants(root) + 1;
	}

	public int height() {
		return height(root);
	}

	private int height(Node<E> node) {
		if (node == null) return 0;

		int maxHeight = 0;
		for (Node<E> child : node.getChildren()) {
			int childHeight = height(child);
			maxHeight = Math.max(maxHeight, childHeight);
		}
		return maxHeight + 1;
	}

	public void clear() {
		this.root = null;
	}

	public E traverseLastValid(E[] list) {
		return traverseLastValid(root, list, 0, null);
	}

	private E traverseLastValid(Node<E> node, E[] list, int index, E lastValid) {
		if (node == null || index >= list.length) return lastValid;
		if (!node.getData().equals(list[index])) return lastValid;

		if (index == list.length - 1) return node.getData();

		E data = node.getData();
		for (Node<E> child : node.getChildren()) data = traverseLastValid(child, list, index + 1, data);

		return data;
	}

	public E traverseToList(E[] list) {
		return traverseToList(root, list, 0);
	}

	private E traverseToList(Node<E> node, E[] list, int index) {
		if (node == null || index >= list.length) return null;
		if (!node.getData().equals(list[index])) return null;

		if (index == list.length - 1) return node.getData();

		for (Node<E> child : node.getChildren()) {
			E result = traverseToList(child, list, index + 1);
			if (result != null) return result;
		}

		return null;
	}

	@Nullable
	public E find(E key) {
		Node<E> found = findNode(root, key);
		if (found == null) return null;
		return found.getData();
	}

	private Node<E> findNode(Node<E> node, E key) {
		if (node == null) return null;

		if (node.getData().equals(key)) return node;

		Node<E> found;
		for (Node<E> child : node.getChildren()) {
			found = findNode(child, key);
			if (found != null) return found;
		}
		return null;
	}

	public boolean isDescendant(Node<E> ancestor, Node<E> descendant) {
		if (ancestor == null || descendant == null || ancestor == descendant) return false;

		return isDescendantRec(ancestor, descendant);
	}

	private boolean isDescendantRec(Node<E> ancestor, Node<E> descendant) {
		for (Node<E> child : ancestor.getChildren())
			if (child == descendant || isDescendantRec(child, descendant)) return true;
		return false;
	}

	public List<Node<E>> getAllNodes() {
		List<Node<E>> nodes = new ArrayList<>();
		buildTreeInfo(root, nodes);
		return nodes;
	}

	private void buildTreeInfo(Node<E> node, List<Node<E>> list) {
		list.add(node);
		for (Node<E> child : node.getChildren()) buildTreeInfo(child, list);
	}

	@NotNull
	@Override
	public Iterator<E> iterator() {
		return new TreeIterator();
	}

	@Override
	public String toString() {
		return getAllNodes().stream().map(Object::toString).toList().toString();
	}

	@Getter
	public static class Node<T> implements Cloneable {

		private final List<Node<T>> children;
		private final T             data;

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
		public Node<T> clone() {
			try {
				@SuppressWarnings("unchecked") Node<T> clonedNode = (Node<T>) super.clone();
				
				return clonedNode;
			} catch (CloneNotSupportedException exception) {
				throw new PluginException(exception);
			}
		}

		@Override
		public String toString() {
			return String.format("[data=%s,parent=%s]", data, parent == null ? "null" : parent.getData());
		}

	}

	private class TreeIterator implements Iterator<E> {

		private final List<Node<E>> nodes;
		private       int           currentIndex;

		private TreeIterator() {
			this.nodes = getAllNodes();
			this.currentIndex = 0;
		}

		@Override
		public boolean hasNext() {
			return currentIndex < nodes.size();
		}

		@Override
		public E next() {
			if (!hasNext()) throw new NoSuchElementException("No more elements in the tree.");
			return nodes.get(currentIndex++).getData();
		}

	}


}
