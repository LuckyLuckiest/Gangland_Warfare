package datastructure;

import me.luckyraven.util.datastructure.LinkedList;
import org.junit.Test;

public class LinkedListTest {

	@Test
	public void testList() {
		LinkedList<String> list = new LinkedList<>();

		list.add("One");
		list.add("Two");
		list.add("Three");
		list.add("Four");
		list.add("Five");

		for (LinkedList.Node<String> values : list) {
			if (values.getNext() != null) {
				System.out.println(values.getData() + " " + values.getNext().getData());
			}
		}
	}

}
