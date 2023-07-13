package me.luckyraven.datastructure;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Tree<E> {

	@Getter
	@Setter
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

	public List<Node<E>> getAllNodes() {
		List<Node<E>> nodes = new ArrayList<>();
		buildTreeInfo(root, nodes);
		return nodes;
	}

	private void buildTreeInfo(Node<E> node, List<Node<E>> list) {
		list.add(node);
		for (Node<E> child : node.getChildren()) buildTreeInfo(child, list);
	}

	@Override
	public String toString() {
		return getAllNodes().stream().map(Object::toString).toList().toString();
	}

}
