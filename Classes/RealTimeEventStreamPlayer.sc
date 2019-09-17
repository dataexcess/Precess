RealTimeEventStreamPlayer {

	var patternDur = 4, baseBarBeat, <>sequence, <>pattern, <>stream, <>player, <>midiOut, <>synthDef;

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
			barline = clock.nextBar,
			nextTime, delta, restTime, endTime;
			baseBarBeat = barline;

			//start with the first node in the sequence
			node = sequence.nodeAt(0);
			while {
				//While we have items in the sequence do this loop (infinite)
				item = node.tryPerform(\obj);
				item.notNil
			} {
				//Our next event's start-time
				nextTime = barline + item[\time];

				//if this is bigger then current beats this means we are in the start of our loop ->
				//so we yield silence till the first event starts
				if(clock.beats < nextTime) {
					inval = Event.silent(nextTime - clock.beats).yield;
				};

				// now we arrived at the event
				//calculate the end time of the event
				endTime = item[\time] + item[\sustain];

				// calculate the "rest" time after this node.
				// if it's last in sequence we substract it with the 'next-bar' time a.k.a. sequence length
				if(node.next.notNil) {
					restTime = node.next.obj[\time] - endTime;
					delta =  node.next.obj[\time] - item[\time]; //used for next-barline calculation
				} {
					restTime = patternDur - endTime;
					delta =  patternDur - item[\time]; //used for next-barline calculation
				};

				//we need to update the 'next-bar' variable once we will reach the end of the current bar-sequence
				if(clock.beats + delta - barline >= patternDur) {
					barline = barline + patternDur;
					baseBarBeat = barline;
				};

				if( node.next.notNil and: {  node.next.obj[\time] <  endTime } , {
					//whenever there is a crossover with notes (multi-voiced) we need to calculate the legato
					var dur, legato;
					dur = node.next.obj[\time] - item[\time];
					legato = item[\sustain] / dur;
					inval = item.copy.put(\dur, dur).put(\legato, legato).put(\note, 0);

					if( midiOut.notNil, {
						inval = inval.copy.put(\type, \midi).put(\midiout, midiOut).put(\channel, 1).put(\midinote, 0).yield;
					},{
						inval = inval.copy.put(\type, \note).put(\instrument, synthDef).yield;
					});

				}, {
					//smoothly lined up with rests
					inval = item.copy.put(\dur, item[\sustain]).put(\note, 0);

					if( midiOut.notNil, {
						inval = inval.copy.put(\type, \midi).put(\midiout, midiOut).put(\channel,1).put(\midinote, 0).yield;
					},{
						inval = inval.copy.put(\type, \note).put(\instrument, synthDef).yield;
					});

					inval =  Event.silent( restTime ).yield;
				});

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
		phaseNow = (player.clock.beats - baseBarBeat) % patternDur;

		//we search for the next node to play in the sequence given the current phase
		nextNodeToPlay = sequence.detect { |item| item[\time] >= phaseNow };

		//if we found one we take its time-value as a starting-point to schedule our next player
		if(nextNodeToPlay.notNil) {
			reschedTime = baseBarBeat + nextNodeToPlay[\time];
		} {
			//if not we just take the next bar
			reschedTime = (player.clock.beats - baseBarBeat).roundUp(patternDur);
		};

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

		//this.reschedule.(time);
	}
}