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
public class Tree<E> implements Iterable<E>, Cloneable {

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

	public void clear() {
		this.root = null;
	}

	public E traverseLastValid(E[] list) {
		return traverseLastValid(root, list, 0, null);
	}

	public E traverseToList(E[] list) {
		return traverseToList(root, list, 0);
	}

	@Nullable
	public E find(E key) {
		Node<E> found = findNode(root, key);
		if (found == null) return null;
		return found.getData();
	}

	public boolean isDescendant(Node<E> ancestor, Node<E> descendant) {
		if (ancestor == null || descendant == null || ancestor == descendant) return false;

		return isDescendantRec(ancestor, descendant);
	}

	public List<Node<E>> getAllNodes() {
		List<Node<E>> nodes = new ArrayList<>();
		buildTreeInfo(root, nodes);
		return nodes;
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

	@Override
	public Tree<E> clone() {
		try {
			@SuppressWarnings("unchecked") Tree<E> tree = (Tree<E>) super.clone();

			// deep cloning every node connected to it
			tree.root = this.root.clone();

			return tree;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	private int height(Node<?> node) {
		if (node == null) return 0;

		int maxHeight = 0;
		for (Node<?> child : node.getChildren()) {
			int childHeight = height(child);
			maxHeight = Math.max(maxHeight, childHeight);
		}
		return maxHeight + 1;
	}

	private E traverseLastValid(Node<E> node, E[] list, int index, E lastValid) {
		if (node == null || index >= list.length) return lastValid;
		if (!node.getData().equals(list[index])) return lastValid;

		if (index == list.length - 1) return node.getData();

		E data = node.getData();
		for (Node<E> child : node.getChildren()) data = traverseLastValid(child, list, index + 1, data);

		return data;
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

	private boolean isDescendantRec(Node<E> ancestor, Node<E> descendant) {
		for (Node<E> child : ancestor.getChildren())
			if (child == descendant || isDescendantRec(child, descendant)) return true;
		return false;
	}

	private void buildTreeInfo(Node<E> node, List<Node<E>> list) {
		list.add(node);
		for (Node<E> child : node.getChildren()) buildTreeInfo(child, list);
	}

	@Getter
	public static class Node<T> implements Cloneable {

		private final T             data;
		private       List<Node<T>> children;

		@Setter
		private Node<T> parent;

		public Node(T data) {
			this.data     = data;
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

				// deep clone
				List<Node<T>> children = new ArrayList<>();

				for (Node<T> child : this.children)
					children.add(child.clone());

				clonedNode.children = children;

				Node<T> parent = this.parent;
				if (parent != null) clonedNode.parent = parent.clone();

				return clonedNode;
			} catch (CloneNotSupportedException exception) {
				throw new PluginException(exception);
			}
		}

		@Override
		public String toString() {
			return String.format("[data=%s,parent=%s]", data, parent == null ? "NA" : parent.getData());
		}

	}

	private class TreeIterator implements Iterator<E> {

		private final List<Node<E>> nodes;
		private       int           currentIndex;

		private TreeIterator() {
			this.nodes        = getAllNodes();
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
