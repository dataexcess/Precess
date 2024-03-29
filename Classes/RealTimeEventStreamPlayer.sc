RealTimeEventStreamPlayer {

	var <>clock, patternDur = 4, barline, <>sequence, <>pattern, <>stream, <>player, <>midiOut, <>currentNode, <>note, isResting = true, <>isRunning = false;

	*new { |note midiOut|
		^super.new.init(note, midiOut);
	}

	init { |note midiOut|
		this.note = note ?? 0;
		this.sequence = PrecessLinkedList.new;
		// this.clock = TempoClock; //or LinkClock
		this.midiOut = midiOut;
		this.pattern = Prout { |inval|

			var item, nextTime, delta, currentBar, barline = clock.nextBar, currentItem;

			//infinite loop
			while { true }
			{
				//full bar silence inserted every bar if sequence is empty
				if( sequence.isEmpty, {
					currentBar = clock.bars2beats(clock.bar);
					if(clock.nextBar <= currentBar, { barline = currentBar + clock.beatsPerBar }, { barline = clock.nextBar });
					inval = Event.silent( barline - clock.beats).yield;
				}, {
					//otherwise we start looping the sequence as long as it has nodes starting with the first
					currentNode = sequence.nodeAt(0);

					//start the loop
					while {
						currentNode.notNil;
						currentItem = currentNode.tryPerform(\obj); //get the node's event
						currentItem.notNil;
					} {

						//clock.postln;

						//get the current bar
						currentBar = clock.bars2beats(clock.bar);

						//Our next event's start-time
						nextTime = currentBar + currentItem[\time];

						//if the time-indicator is smaller then our next event we insert a rest (time before first note)
						if(clock.beats < nextTime, {

							//player.clock.beats.postln;

							//this is the rest-period
							isResting = true;

							//rest duration equals time now until start-time of next-event
							inval = currentItem.copy.put(\type, \rest).put(\dur, nextTime - clock.beats).yield;
						},{
							//no more resting
							isResting = false;

							//calculate the event's duration (not sustain)
							if(currentNode.next.notNil) {
								//use time until next node
								delta =  currentNode.next.obj[\time] - currentItem[\time];
							} {
								//if it is the last use the next-bar as reference
								delta =  clock.beatsPerBar - currentItem[\time];
							};

							//we prepare our Event
							if( midiOut.notNil, {
								//midi
								inval = currentItem.copy.put(\type, \midi).put(\midiout, this.midiOut).put(\chan, 1).put(\midinote, this.note);
							},{
								//test-sound
								inval = currentItem.copy.put(\type, \note, \instrument, \default).put(\note, this.note);
							});

							//our event's duration and sustain
							inval = inval.copy.put(\dur, delta, \sustain, currentItem[\sustain]).yield;

							//current Node might be null if we switch to an empty preset
							if (currentNode.notNil, {

								//continue the loop
								currentNode = currentNode.next;

								//if next node is nil set it to the first. If not, exit loop.
								if(currentNode.isNil, {
									if (sequence.isEmpty.not, {
										currentNode = sequence.nodeAt(0);
									});
								});
							});
						})
					}
				});
			};
		};

		//save the pattern as a stream
		this.stream = pattern.asStream;
	}

	start {
		player = EventStreamPlayer(stream).play(clock, doReset: true); //TempoClock
		isRunning = true;
	}

	stop {
		player.stop();
		player.reset();
		isRunning = false;
	}

	clearAll {
		currentNode = nil;
		sequence = PrecessLinkedList();
	}

	reschedule {
		var phase, reschedTime, clock, currentBar, nextBar, nextItem, nextNode;

		clock = player.clock;

		//some variables to work with
		phase = clock.beatInBar;
		currentBar = clock.bars2beats(clock.bar);
		if(clock.nextBar <= currentBar, { nextBar = currentBar + clock.beatsPerBar }, { nextBar = clock.nextBar });

		//we search for the next node to play in the sequence given the current phase
		nextItem = sequence.detect { |item| item[\time] >= phase };

		//schedule the player
		if(nextItem.notNil, {
			//schedule on the next first node
			reschedTime = currentBar + nextItem[\time];
			nextNode = sequence.findNodeOfObj(nextItem);

			if (nextNode.prev.notNil, {
				if(	isResting.not, {
					//only do this when we are not waiting until the first note to play
					//otherwise this will mess up the sequence
					currentNode = nextNode.prev;
				});
			},{
				currentNode = sequence.nodeAt(0);
			});
		},{
			currentNode = sequence.last;
			reschedTime = nextBar;
		});

		//swap out stream players
		//clock = player.clock;
		player.stop;

		player = EventStreamPlayer(stream);
		clock.schedAbs(reschedTime, player.refresh); //player.refresh
		^nextNode;
	}

	switchToSequence { |newSequence|
		sequence = newSequence.deepCopy;
		this.reschedule;
	}

	insertNote { |time, sustain|
		var node, new, idx;

		idx = 0;

		if( sequence.size == 0, {
			//if empty insert first node
			sequence.addFirst( (sustain: sustain, time: time) )
		},{
			//search for place to insert
			node = sequence.nodeAt(0);
			while {
				node.notNil and: { node.obj[\time] < time }
			} {
				node = node.next;
				idx = idx + 1;
			};

			//we found the place to insert our new node
			new = (sustain: sustain, time: time);

			if(node.notNil, {
				sequence.insertAt(idx, new);
			}, {
				sequence.add(new);
			});
		});

		if( isRunning, { this.reschedule });
	}

	deleteNote { |time, sustain|
		var node, next, idx;
		idx = 0;

		// search for node to delete
		node = sequence.nodeAt(0);
		while {
			node.notNil and: {
				((node.obj[\time] == time) && (node.obj[\sustain] == sustain)).not
			}
		} {
			node = node.next;
			idx = idx + 1;
		};

		if(node.notNil, {
			sequence.removeAt(idx);
			("removed at "+idx).postln;
		});

		if( isRunning, { this.reschedule });
	}
}
