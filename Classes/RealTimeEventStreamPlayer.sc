RealTimeEventStreamPlayer {

	var patternDur = 4, baseBarBeat, <>sequence, <>pattern, <>stream, <>player;

	*new {
		^super.new.init();
	}

	init {
		this.sequence = LinkedList.new;
		[
		(degree: 0, time: 0, sustain: 0.2),
		(degree: 0, time: 2, sustain: 0.2)
		].do { |item| sequence.add(item) };

		// can't just Pseq because we need to account for insertions
		this.pattern = Prout { |inval|
			var item, node,

			// clock = thisThread.clock,
			clock = TempoClock,
			barline = clock.nextBar,
			nextTime, delta;
			// the rescheduling function needs to be aware
			// of the most recent phrase boundary for this thread
			baseBarBeat = barline;
			// nodeAt is "private" but we need access to the chain

			if (sequence.size > 0, {
				node = sequence.nodeAt(0);
				while {
					item = node.tryPerform(\obj);
					item.notNil
				} {
					// wait until next time

					item.postln;

					nextTime = barline + item[\time];
					if(clock.beats < nextTime) {
						inval = Event.silent(nextTime - clock.beats).debug("rest").yield;
					};
					[clock.beats, item].debug("got item");
					// now update counters and do this one
					if(node.next.notNil) {
						delta = node.next.obj[\time] - item[\time];
					} {
						delta = patternDur - item[\time];
					};
					if(clock.beats + delta - barline >= patternDur) {
						barline = barline + patternDur;
						baseBarBeat = barline;
					};
					inval = item.copy.put(\dur, delta).yield;
					node = node.next;
					if(node.isNil) { node = sequence.nodeAt(0) };  // loop
				}
			});
		};

		this.stream = pattern.asStream;
		this.player = EventStreamPlayer(stream).play(doReset: true);
	}

	getBeats {
		var clock = TempoClock;
		// clock.beats.postln;
		^clock.beats
	}

	/*	setTempo { | tempo |
	thisThread.clock.tempo = tempo;
	}*/

	// functions to change sequence
	reschedule { |time|
		var phaseNow, reschedTime, nextToPlay, clock;

		phaseNow = (player.clock.beats - baseBarBeat) % patternDur;
		phaseNow.debug("phaseNow");
		nextToPlay = sequence.detect { |item| item[\time] >= phaseNow };
		nextToPlay.debug("nextToPlay");
		if(nextToPlay.notNil) {
			reschedTime = baseBarBeat + nextToPlay[\time];
		} {
			// next "pattern barline":
			reschedTime = (player.clock.beats - baseBarBeat).roundUp(patternDur);
		};
		reschedTime.debug("reschedTime");

		// swap out stream players
		clock = player.clock;
		player.stop;
		player = EventStreamPlayer(stream);
		clock.schedAbs(reschedTime, player.refresh);
	}

	insertNote { |time, sustain|
		var node, new;
		// search for place to insert

		if (sequence.size > 0, {
			node = sequence.nodeAt(0);
			while {
				node.notNil and: { node.obj[\time] < time }
			} {
				node = node.next;
			};
			new = LinkedListNode((sustain: sustain, time: time));
			if(node.notNil) {
				// change A --> C into A --> B --> C; B = new; C = node
				new.prev = node.prev;  // B <-- A
				node.prev.next = new;  // A --> B
				new.next = node;  // B --> C
				node.prev = new;  // C <-- B
			} {
				new.prev = sequence.last;  // add at the end
				sequence.last.next = new;
			}
		},{
			new = LinkedListNode((sustain: sustain, time: time));
			sequence.addFirst(new);
			"not working - after insert?".postln;
		});

		this.reschedule.(time);
	}

	deleteNote { |time, sustain|
		var node, next;
		// search for node to delete

		if (sequence.size > 0, {
			node = sequence.nodeAt(0);
			while {
				node.notNil and: {
					node.obj[\time] != time and: { node.obj[\sustain] != sustain }
				}
			} {
				node = node.next;
			};
			if(node.notNil) {
				next = node.next;
				next.prev = node.prev;
				node.prev.next = next;
			};
			// not really necessary to reschedule
			// the Prout will add rests automatically
			// if the next note to play was deleted
		});
	}
}


// hit this during beat 2
// ~insertNote.(1.75, 8);

/*got item: [ 1420.0, ( 'degree': 0, 'time': 0 ) ]
got item: [ 1421.0, ( 'degree': 1, 'time': 1 ) ]
phaseNow: 1.292918184
nextToPlay: ( 'degree': 8, 'time': 1.75 )
reschedTime: 1421.75
-> a TempoClock
// YES, plays at the right time
got item: [ 1421.75, ( 'degree': 8, 'time': 1.75 ) ]
got item: [ 1422.0, ( 'degree': 2, 'time': 2 ) ]*/

// hit this during beat 2
//~deleteNote.(1.75, 8);
/*
got item: [ 1428.0, ( 'degree': 0, 'time': 0 ) ]
got item: [ 1429.0, ( 'degree': 1, 'time': 1 ) ]
-> a LinkedListNode
// thread wakes up at 1429.75,
// and skips over the deleted note
rest: ( 'dur': Rest(0.25), 'delta': 0.25 )
got item: [ 1430.0, ( 'degree': 2, 'time': 2 ) ]*/
