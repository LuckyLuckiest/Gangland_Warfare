package me.luckyraven.datastructure;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

@Getter
public class LinkedList<E extends Comparable<E>> implements Iterable<LinkedList.Node<E>>, Cloneable {

	private int     size;
	private Node<E> head, tail;

	public LinkedList() {
		this.size = 0;
		this.head = tail = null;
	}

	@Nullable
	public E get(int index) {
		if (index >= size) return null;

		Node<E> current = head;

		for (int i = 0; i < size; i++) {
			if (i == index) return current.getData();

			current = current.next;
		}

		return null;
	}

	public void add(E data) {
		add(new Node<>(data));
	}

	public void add(Node<E> node) {
		if (head == null) head = tail = node;
		else {
			node.previous = tail;
			tail.next     = node;
			tail          = node;
		}

		size++;
	}

	public void addSorted(E data) {
		addSorted(new Node<>(data));
	}

	public void addSorted(Node<E> node) {
		if (head == null) {
			head = tail = node;
		} else {
			Node<E> current    = head;
			E       targetData = node.data;

			// traverse to find the proper order
			while (current != null && current.data.compareTo(targetData) < 0) current = current.next;

			// add after tail
			if (current == null) {
				node.previous = tail;
				tail.next     = node;
				tail          = node;
			}
			// add before head
			else if (current.previous == null) {
				node.next     = head;
				head.previous = node;
				head          = node;
			}
			// add between elements
			else {
				current.previous.next = node;
				node.previous         = current.previous;
				node.next             = current;
				current.previous      = node;
			}
		}

		size++;
	}

	public boolean remove(int index) {
		Node<E> current = head;

		for (int i = 0; i < size; i++) {
			if (i == index) return remove(current.data);

			current = current.next;
		}

		return false;
	}

	public boolean remove(E data) {
		Node<E> current = head;
		boolean found   = false;

		while (current != null) {
			if (current.data.equals(data)) {
				if (current == head) head = head.next;
				if (current == tail) tail = tail.previous;
				if (current.previous != null) current.previous.next = current.next;
				if (current.next != null) current.next.previous = current.previous;

				size--;
				found = true;
				break;
			} else current = current.next;
		}

		return found;
	}

	public void removeAllOccurrences(E data) {
		Node<E> current = head;

		while (current != null) {
			if (current.data.equals(data)) {
				if (current == head) head = head.next;
				if (current == tail) tail = tail.previous;
				if (current.previous != null) current.previous.next = current.next;
				if (current.next != null) current.next.previous = current.previous;

				size--;
			} else current = current.next;
		}
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public int lastIndexOf(E data) {
		Node<E> current = tail;
		int     i       = size - 1;

		while (current != null) {
			if (current.data.equals(data)) return i;

			current = current.previous;
			i--;
		}

		return i;
	}

	public List<E> toList() {
		List<E> list = new java.util.LinkedList<>();

		forEach(value -> list.add(value.getData()));

		return list;
	}

	@Override
	public @NotNull Iterator<LinkedList.Node<E>> iterator() {
		return new LinkIterator<>(this.head);
	}

	@Override
	public LinkedList<E> clone() {
		try {
			return (LinkedList<E>) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Getter
	public static class Node<T extends Comparable<T>> implements Cloneable {

		private final T       data;
		private       Node<T> previous, next;

		public Node(T data) {
			this(data, null, null);
		}

		public Node(T data, Node<T> previous, Node<T> next) {
			this.data     = data;
			this.previous = previous;
			this.next     = next;
		}

		@Override
		public Node<T> clone() {
			try {
				return (Node<T>) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}

	}

	private static class LinkIterator<E extends Comparable<E>> implements Iterator<Node<E>> {
		private Node<E> current;

		public LinkIterator(Node<E> root) {
			this.current = root;
		}

		@Override
		public boolean hasNext() {
			return current != null;
		}

		@Override
		public Node<E> next() {
			if (current == null) {
				throw new NoSuchElementException();
			}

			Node<E> temp = current;
			current = current.next;

			return temp;
		}
	}

}
