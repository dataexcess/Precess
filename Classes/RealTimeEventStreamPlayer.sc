RealTimeEventStreamPlayer {

	var patternDur = 4, barline, <>sequence, <>pattern, <>stream, <>player, <>midiOut, <>synthDef;

	*new {
		^super.new.init();
	}

	init {
		this.sequence = PrecessLinkedList.new;

		this.synthDef = synthDef ?? \default;

		// can't just Pseq because we need to account for insertions
		this.pattern = Prout { |inval|

			//Starting variables
			var item, node,
			clock = TempoClock,
			nextTime, delta, restTime, endTime;

			barline = clock.nextBar;

			//start with the first node in the sequence
			node = sequence.nodeAt(0);
			while {
				//While we have items in the sequence do this loop (infinite)
				item = node.tryPerform(\obj);
				item.notNil
			} {
				// barline = (clock.beats - (clock.beats % patternDur));

				//Our next event's start-time
				nextTime = barline + item[\time];

				//if the time-indicator is smaller then our next event we insert a wait
				//this is always true for a first note in the sequence not placed at 0 (start-time)
				if(clock.beats < nextTime) {
					// "silent inserted".postln;
					inval = Event.silent(nextTime - clock.beats).yield;
				};

				// if it's last in sequence we substract it with the 'next-bar' time a.k.a. sequence length
				if(node.next.notNil) {
					delta =  node.next.obj[\time] - item[\time]; //used for next-barline calculation
				} {
					delta =  patternDur - item[\time]; //used for next-barline calculation
				};

				//we need to update the 'next-bar' variable once we will reach the end of the current bar-sequence
				if(clock.beats + delta - barline >= patternDur) {
					// "reached".postln;
					barline = barline + patternDur;
				};

				//we prepare our Event
				if( midiOut.notNil, {
					inval = item.copy.put(\type, \midi).put(\midiout, midiOut).put(\chan, 1).put(\midinote, 1);

				},{
					inval = item.copy.put(\type, \note, \instrument, synthDef).put(\note, 0);
				});

				inval = inval.copy.put(\dur, delta, \sustain, item[\sustain]).yield;

				//continue the loop
				node = node.next;
				if(node.isNil) { node = sequence.nodeAt(0) };
			}
		};

		this.stream = pattern.asStream;
	}

	getBeats {
		var clock = TempoClock;
		^clock.beats
	}

	start {
		player = EventStreamPlayer(stream).play(doReset: true);
	}

	stop {
		player.stop();
		player.reset();
	}

	// functions to change sequence
	reschedule { |time|
		var phaseNow, reschedTime, nextNodeToPlay, clock;

		//this gives us the phase of the current bar [0-patternLength]
		phaseNow = (player.clock.beats - barline) % patternDur;

		//we search for the next node to play in the sequence given the current phase
		nextNodeToPlay = sequence.detect { |item| item[\time] >= phaseNow };

		//if nextNodeToPlay is last in sequence and we already past the previous last note...
		//we need to reset the barline to the current one
		if( nextNodeToPlay == sequence.last and: { phaseNow > sequence.findNodeOfObj(sequence.last).prev.obj[\time]  }, {
			barline = barline - patternDur;
		});

		//if we found one we take its time-value as a starting-point to schedule our next player
		if(nextNodeToPlay.notNil) {
			reschedTime = barline + nextNodeToPlay[\time];
		} {
			//if not we just take the next bar
			reschedTime = (player.clock.beats - barline).roundUp(patternDur);
		};

		//swap out stream players
		clock = player.clock;
		player.stop;
		player = EventStreamPlayer(stream);
		clock.schedAbs(reschedTime, player.refresh);
	}

	switchToSequence { |newSequence|
		var phaseNow, reschedTime, nextNodeOld, nextNodeNew, clock;

		//this gives us the phase of the current bar [0-patternLength]
		phaseNow = (player.clock.beats - barline) % patternDur;

		//we search for the next node to play in the sequence given the current phase
		nextNodeOld= sequence.detect { |item| item[\time] >= phaseNow };

		nextNodeNew = newSequence.detect { |item| item[\time] >= phaseNow };


		//if nextNodeToPlay is last in sequence and we already past the previous last note...
		//we need to reset the barline to the current one
		if(  nextNodeOld.isNil  and: { nextNodeNew.notNil }, {
			barline = barline - patternDur;
			"RESET".postln;
		});

		//if we found one we take its time-value as a starting-point to schedule our next player
		if(nextNodeNew.notNil) {
			reschedTime = barline + nextNodeNew[\time];
			"rescheduled somewhere before the next bar".postln;
		} {
			//if not we just take the next bar
			reschedTime = (player.clock.beats - barline).roundUp(patternDur);
			"rescheduled FOR next bar".postln;
		};

		this.sequence = newSequence.deepCopy;

		//swap out stream players
		clock = player.clock;
		player.stop;
		player = EventStreamPlayer(stream);
		clock.schedAbs(reschedTime, player.refresh);
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

			if(node.notNil) {
				sequence.insertAt(idx, new);
			} {
				sequence.add(new);
			};

			//we do not need the time argument?
			this.reschedule.(time);
		})
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
		});

		this.reschedule.(time);
	}
}