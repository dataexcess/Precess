PrecessGUI : Window {
	var bounds, precess, player, midiOut, presets, channels, activeChannels, holdingShift, dictionary;

	*new {
		| name bounds |
		^super.new.init(name, bounds);
	}

	init {
		| name bounds |

		var startButton, bpmNumberBox, divisionsNumberBox, midiOutButton;
		this.acceptsMouseOver_(true).background_(Color.black);
		this.bounds = bounds;
		dictionary = Dictionary.new();
		holdingShift = false;

		//MIDI SETUP
		MIDIClient.init(0,1);
		midiOut =  MIDIOut(0, MIDIClient.destinations[1].uid);

		//REALTIME-PLAYER SETUP
		player = RealTimeEventStreamPlayer();
		//player.synthDef = \test_synth;
		player.midiOut = midiOut; //uncomment this line for MIDI OUTPUT on device 1 channel 1 instead of \testSynth

		//PRECESS SETUP
		precess = Precess(this, Rect(0, 20, bounds.width,bounds.width), [2,4,8,16,24,64], player);
		precess.initialiseWithFirstQuarterNote;
		precess.insertNoteCallback = { arg block;
			player.insertNote(block.noteEvent[\time], block.noteEvent[\sustain]);
		};
		precess.deleteNoteCallback = { arg block;
			player.deleteNote(block.noteEvent[\time], block.noteEvent[\sustain]);
		};
		precess.divisionsChangedCallback =  { arg divisions;
			//set new list
			var stringDivisions = this.parseArrayToString(divisions);
			divisionsNumberBox.textField.value = stringDivisions;
		};

		//UI
		startButton = PrecessButton(this, Rect(20, 20, 80, 24));
		startButton.button.states_([["START", Color.white, Color.clear],["STOP", Color.white, Color.clear]]);
		startButton.button.action_({ arg button;
			if (button.value == 1, { precess.start; }, { precess.stop });
		});

		bpmNumberBox = PrecessNumberBox(this, Rect(bounds.width - 100, 20, 80, 24));
		bpmNumberBox.numberBox.action_({ arg numberBox;
			TempoClock.tempo_(numberBox.value/60);
		});

		divisionsNumberBox = PrecessTextField(this, Rect(20, bounds.width - 44, 120, 24));
		divisionsNumberBox.textField.action_({ arg textField;
			var newList, parsed = "";

			//parse
			newList = textField.value.split(Char.comma);
			newList = newList.collect({arg element; element = element[0..1]; element.asInteger});
			newList.removeAllSuchThat({ arg item; item == 0});
			newList.sort;
			newList = newList[0..5];

			//prepare return string
			newList.do({ arg elem, idx;
				parsed = parsed ++ elem;
				if( idx != (newList.size-1), { parsed = parsed ++ ","})
			});
			textField.value = parsed;

			//set new list
			if( newList.notNil, {
				precess.divisions =  newList.asArray;
				precess.setup(precess.bounds);
				precess.userView.refresh;
			});
		});

		midiOutButton = PrecessButton(this, Rect(bounds.width - 100, bounds.width - 44, 80, 24));
		midiOutButton.button.states_([["MIDI", Color.white, Color.clear],["MIDI", Color.white, Color.clear]]);
		midiOutButton.button.action_({ arg button;
			// if (button.value == 1, { precess.start; }, { precess.stop });
		});

		activeChannels = List();
		channels = 12.collect({ arg idx;
			var size = 30;
			var containerWidth = (bounds.width - (20 * 2));
			var leftPadding =  (containerWidth - (size * 12)) / 11;
			var channel = idx+1;

			PrecessButton(this, Rect(20 + (( size + leftPadding) * idx), bounds.width, size, size))
			.button.states_([[channel.asString, Color.white, Color.clear],[channel.asString, Color.black, Color.white]])
			.action_({ arg button;

				var others = channels.copy;
				others.removeAt(idx);
				others.do({ arg other;
					other.value_(0);
					activeChannels.remove(other);
				});

				if( activeChannels.includes(channel), {
					button.value = 1;
				}, {
					activeChannels.add(channel);
				});
			})
		});

		channels[0].valueAction_(1);

		presets = 12.collect({ arg idx;
			var size = 30;
			var containerWidth = (bounds.width - (20 * 2));
			var leftPadding =  (containerWidth - (size * 12)) / 11;
			var channel = idx+1;

			PrecessButton(this, Rect(20 + (( size + leftPadding) * idx), bounds.width + 50, size, size))
			.button.states_([["", Color.white, Color.clear],["●", Color.white, Color.clear], ["●", Color.black, Color.white] ])
			.action_({ arg button;

				if( holdingShift, {
					var snapshot = Dictionary();
					snapshot.add(\discs -> precess.discsCompact);
					snapshot.add(\sequence -> player.sequence.deepCopy);

					if(dictionary[activeChannels].isNil, {
						dictionary[activeChannels] = Array.fill(12, { nil });
					});

					//set a pattern
					dictionary[activeChannels][idx] = snapshot;
					button.value = 1;
				}, {

					//call a pattern
					if( dictionary[activeChannels].notNil, {

						if( dictionary[activeChannels][idx].notNil, {

							var preset = dictionary[activeChannels][idx];

							precess.setDiscs = preset[\discs];
							player.switchToSequence(preset[\sequence]);

							//set state of other buttons
							presets.do({arg otherButton, idx;
								if( dictionary[activeChannels][idx].notNil, { otherButton.value = 1 }, { otherButton.value = 0 });
							});

							button.value = 2;
						},{
							button.value = 0;
						});
					},{
						button.value = 0;
					});
				});
			});
		});

		view.keyModifiersChangedAction_({ arg view,modifiers;
			if( modifiers == 131072, { holdingShift = true });
			if( modifiers == 0, { holdingShift = false });
		});
	}

	parseArrayToString { | divisions |
		var parsed = "";

		divisions.do({ arg elem, idx;
			parsed = parsed ++ elem;
			if( idx != (divisions.size-1), { parsed = parsed ++ ","})
		});

		^parsed;
	}
}

