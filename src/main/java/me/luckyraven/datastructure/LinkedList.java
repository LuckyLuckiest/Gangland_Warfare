package me.luckyraven.datastructure;

import lombok.Getter;

import java.util.NoSuchElementException;

public class LinkedList<E extends Comparable<E>> {

	private Node<E> head, tail;
	private @Getter int size;

	public LinkedList() {
		this.head = this.tail = null;
		this.size = 0;
	}

	public void add(E element) {
		Node<E> node = new Node<>(element), current = head;

		if (current == null) head = tail = node;
		else {
			while (current.next != null) current = current.next;
			node.prev = current;
			current.next = tail = node;
		}
		++size;
	}

	public boolean delete(E element) {
		if (size == 0) throw new NoSuchElementException("There are no elements in the list");

		Node<E> current = head;
		boolean found   = false;

		while (current != null && !found) {
			if (current.getElement().equals(element)) found = true;
			else current = current.next;
		}

		if (!found) return false;

		if (current == head) {
			head = head.next;
		} else if (current == tail) {
			tail = tail.prev;
			tail.next = null;
		} else {
			current.prev.next = current.next;
			current.next.prev = current.prev;
		}
		--size;

		return true;
	}

	public void clear() {
		head = tail = null;
	}

	public void display() {
		if (head == null) {
			System.out.println("The list is empty!");
			return;
		}
		display(head);
	}

	private void display(Node<E> node) {
		if (node == null) return;
		System.out.println(node.getElement());
		display(node.next);
	}

	private static class Node<T extends Comparable<T>> {

		@Getter
		private final T element;

		public Node<T> next, prev;

		public Node(T element) {
			this.element = element;
			this.next = this.prev = null;
		}

	}

}
