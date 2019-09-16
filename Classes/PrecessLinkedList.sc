PrecessLinkedList : LinkedList {

	insertAt { arg index, obj;

		var node = this.nodeAt(index);
		var new = LinkedListNode.new(obj);

		if (index == 0, {
			//HEAD
			if (head.notNil, {
				new.next_(head);
				head.prev_(new);
			});
			head = new;
			size = size + 1;
		},{
			if (index <= (this.size - 1), {
				//IN-BETWEEN
				new.prev = node.prev;  // B <-- A
				if (node.prev.notNil, { node.prev.next = new;}) ; // A --> B
				new.next = node;  // B --> C
				node.prev = new; //  C <-- B

				size = size + 1;
			})
		});

	}
}
