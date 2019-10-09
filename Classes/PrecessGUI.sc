PrecessGUI : Window {
	var precess, clock, players, currentPlayer, presets,
	channels, activeChannel, activeChannels,
	holdingShift, holding_A, holding_Q,
	dictionary, midiOut;

	*new {
		| name bounds |
		^super.new(name, bounds, false).init()
	}

	init {
		var startButton, bpmNumberBox, divisionsNumberBox, midiOutButton, linkButton, loadButton, saveButton, linkClock, noteIncrementCount, lastSelectedBlock;

		this.acceptsMouseOver_(true).background_(Color.black);
		dictionary = Dictionary.new();
		holdingShift = false;
		holding_A = false;
		holding_Q = false;
		clock = TempoClock;
		linkClock = TempoClock; // LinkClock();
		noteIncrementCount = 0;
		lastSelectedBlock = nil;
		MIDIClient.init();
		midiOut = MIDIOut(0);
		players = 12.collect{ arg midiNote;  RealTimeEventStreamPlayer(midiNote, midiOut) };

		view.enabled = true;

		//PRECESS SETUP
		precess = Precess(this, Rect(0, 20, this.bounds.width, this.bounds.width), [2,4,8,16,24,64])

		.noteClickCallback_({ arg block;

			"note callback".postln;
			lastSelectedBlock = block;

			case
			{ holding_A } {

				if( block.isOn.not, {
					//shortcut A -> all insert all blocks in disc
					block.disc.blocks.do({ arg discBlock;
						discBlock.isOn = true;
						currentPlayer.insertNote(discBlock.noteEvent[\time], discBlock.noteEvent[\sustain]);
					});
				},{
					//shortcut A -> remove all blocks in disc
					block.disc.blocks.do({ arg discBlock;
						discBlock.isOn = false;
						currentPlayer.deleteNote(discBlock.noteEvent[\time], discBlock.noteEvent[\sustain]);
					});
				});
			}
			{ holding_Q } {
				//shortcut Q -> insert notes incrementally from this note
				var currentBlock = block;
				var counter = noteIncrementCount;

				if( block != lastSelectedBlock, {
					noteIncrementCount = 0;
					counter = noteIncrementCount;
				});

				while { counter > 0} {
					currentBlock = currentBlock.nextBlock;
					counter = counter - 1;
				};

				if ( currentBlock.isOn.not, {
					currentBlock.isOn = true;
					currentPlayer.insertNote(currentBlock.noteEvent[\time], currentBlock.noteEvent[\sustain]);
				});

				noteIncrementCount = noteIncrementCount + 1;
			}
			{ holding_A.not and: { holding_Q.not } } {

				if( block.isOn.not, {
					block.isOn = true;
					currentPlayer.insertNote(block.noteEvent[\time], block.noteEvent[\sustain]);
				},{
					block.isOn = false;
					currentPlayer.deleteNote(block.noteEvent[\time], block.noteEvent[\sustain]);
				});
			};

			this.saveTemporarySnapshot;
		})
		.noteDrawCallback_({ arg block;
			if (block.isOn.not, {
				block.isOn = true;
				currentPlayer.insertNote(block.noteEvent[\time], block.noteEvent[\sustain]);
			});
		})
		.noteRightDragCallback_({ arg block, value;
			value.round (0.1).postln;
		})
		.noteLeftDragCallback_({ arg block, value;
			block.disc.blocks.do({ arg discBlock;
				discBlock.isOn = value.coin.postln;
				//currentPlayer.insertNote(discBlock.noteEvent[\time], discBlock.noteEvent[\sustain]);
			});
			precess.userView.refresh;
		})
		.divisionsChangedCallback_({ arg divisions;
			var stringDivisions = this.parseArrayToString(divisions);
			divisionsNumberBox.textField.value = stringDivisions;
		});

		//UI
		startButton = PrecessButton(this, Rect(20, 20, 78, 24));
		startButton.button.states_([["START", Color.white, Color.clear],["STOP", Color.white, Color.clear]]);
		startButton.button.action_({ arg button;
			if( button.value == 1, {
				clock.stop;
				if( linkButton.button.value == 1, { clock = linkClock}, { clock = TempoClock()} );
				clock.tempo_(bpmNumberBox.numberBox.value / 60 );
				players.do{ arg player;
					player.clock = clock;
					player.start
				};
				precess.clock = clock;
				precess.start;
			}, {
				players.do{ arg player; player.stop; };
				precess.stop;
				//clock.stop;
			});
		});

		loadButton = PrecessButton(this, Rect(97, 20, 25, 24));
		loadButton.button.states_([["L", Color.white, Color.clear]]);
		loadButton.button.action_({ arg button;
			"LOAD".postln;
		});

		saveButton = PrecessButton(this, Rect(121, 20, 25, 24));
		saveButton.button.states_([["S", Color.white, Color.clear]]);
		saveButton.button.action_({ arg button;
			"SAVE".postln;
		});

		PrecessPopUpMenu(this,  Rect(this.bounds.width - 146, 20, 126, 24))
		.popUpMenu.items_(MIDIClient.destinations.collect({arg endpoint; endpoint.device + " - " + endpoint.name}))
		.action_({arg menu;
			var client = MIDIClient.destinations.at(menu.value);
			midiOut = MIDIOut.newByName(client.device, client.name);
			players.do({arg player; player.midiOut = midiOut});
		});

		divisionsNumberBox = PrecessTextField(this, Rect(20, this.bounds.width - 44, 126, 24));
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
				precess.clearBlocks;
				players[activeChannel].clearAll;
				players[activeChannel].reschedule;
			});
		});

		linkButton = PrecessButton(this, Rect(this.bounds.width - 146, this.bounds.width - 44, 49, 24));
		linkButton.button.states_([["LINK", Color.white, Color.clear],["LINK", Color.black, Color.white]])
		.action_({ arg button;
			if( button.value == 1, {
				"link is ON".postln;
			},{
				"link is OFF".postln;
			});
		});

		bpmNumberBox = PrecessNumberBox(this, Rect(this.bounds.width - 98, this.bounds.width - 44, 78, 24));
		bpmNumberBox.numberBox.action_({ arg numberBox;
			var tempo = numberBox.value/60;
			clock.tempo_(tempo);
			players.do{ arg player; player.clock.tempo_(tempo)};
		});

		activeChannels = List();
		channels = 12.collect({ arg idx;
			var size = 30;
			var containerWidth = (this.bounds.width - (20 * 2));
			var leftPadding =  (containerWidth - (size * 12)) / 11;
			var channel = idx+1;

			PrecessButton(this, Rect(20 + (( size + leftPadding) * idx), this.bounds.width, size, size))
			.button.states_([[channel.asString, Color.white, Color.clear],[channel.asString, Color.black, Color.white]])
			.action_({ arg button;

				dictionary.postln;

				activeChannels.clear;
				channels.do{ arg channel; channel.value_(0) };
				activeChannels.add(idx);
				button.value = 1;
				activeChannel = idx;

				precess.clearBlocks;
				currentPlayer = players[idx];

				if (dictionary[activeChannels].isNil, {
					var dict =  Dictionary();
					dict.add(\presets -> Array.fill(13, { nil })); //last one is temp placeholder
					dict.add(\active -> 12); //set active to temp placeholder
					dict.add(\lastActive -> 12); //set active to temp placeholder
					dictionary[activeChannels.copy] = dict;

					//buttons states
					presets.do({arg button; button.value = 0 });
				},{
					var activePreset, lastActivePreset;
					activePreset = dictionary[activeChannels][\active];
					lastActivePreset = dictionary[activeChannels][\lastActive];

					presets.do({ arg preset, presetIdx;

						if( dictionary[activeChannels][\presets][presetIdx].isNil, { preset.value = 0 }, { preset.value = 1 });

						if( activePreset == 12, {
							//we are in temp space -> display last active and load current active to discs
							if( presetIdx == lastActivePreset, {preset.value = 2});
							//set the discs if not nil
							if (dictionary[activeChannels][\presets][activePreset].notNil, {
								precess.setDiscs = dictionary[activeChannels][\presets][activePreset][\discs];
							});
						},{
							//we are in saved space -> display active and load current active to discs
							if( presetIdx == activePreset, {preset.value = 3});
							//set the discs if not nil
							if (dictionary[activeChannels][\presets][activePreset].notNil, {
								precess.setDiscs = dictionary[activeChannels][\presets][activePreset][\discs];
							});
						});
					});
				});
			})
		});

		channels[0].valueAction_(1);

		presets = 12.collect({ arg idx;
			var size = 30;
			var containerWidth = (this.bounds.width - (20 * 2));
			var leftPadding =  (containerWidth - (size * 12)) / 11;
			var channel = idx+1;

			PrecessButton(this, Rect(20 + (( size + leftPadding) * idx), this.bounds.width + 50, size, size))
			.button.states_([["", Color.white, Color.clear],["●", Color.white, Color.clear],["○", Color.black, Color.white],["●", Color.black, Color.white]])
			.action_({ arg button;

				if( holdingShift, {

					var snapshot = Dictionary();
					snapshot.add(\discs -> precess.discsCompact);
					snapshot.add(\sequence -> currentPlayer.sequence.deepCopy);

					//buttons state
					presets.do({arg button, buttonIdx;
						if( dictionary[activeChannels][\presets][buttonIdx].notNil, { button.value = 1 }, { button.value = 0 });
					});
					button.value = 3;

					//set a pattern
					dictionary[activeChannels][\presets][idx] = snapshot;
					dictionary[activeChannels][\active] = idx;
				}, {

					//call a pattern
					if( dictionary[activeChannels].notNil, {

						if( dictionary[activeChannels][\presets][idx].notNil, {

							var preset = dictionary[activeChannels][\presets][idx];

							precess.setDiscs = preset[\discs];
							currentPlayer.switchToSequence(preset[\sequence]);
							dictionary[activeChannels][\active] = idx;
							dictionary[activeChannels][\lastActive] = idx;

							//set state of other buttons
							presets.do({arg otherButton, idx;
								if( dictionary[activeChannels][\presets][idx].notNil, { otherButton.value = 1 }, { otherButton.value = 0 });
							});

							button.value = 3;
						},{
							button.value = 0;
						});
					},{
						button.value = 0;
					});
				});
			});
		});

		view.keyDownAction_({ arg view, char, modifiers, unicode, keycode, key;
			unicode.postln;
			view.enabled.postln;

			if( unicode == 97, { holding_A = true });
			if( unicode == 113, { holding_Q = true });
			if( unicode == 119, { precess.isDrawing = true });
		});

		view.keyUpAction_({ arg view, char, modifiers, unicode, keycode, key;
			if( unicode == 97, { holding_A = false });
			if( unicode == 113, { holding_Q = false });
			if( unicode == 119, { precess.isDrawing = false });
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

	saveTemporarySnapshot {
		var snapshot = Dictionary();

		//if we are not yet in temporary storage
		if( dictionary[activeChannels][\active] != 12, {
			dictionary[activeChannels][\lastActive] = dictionary[activeChannels][\active];
			dictionary[activeChannels][\active] = 12;
			presets[dictionary[activeChannels][\lastActive]].value = 2;
		});

		//save the temporary snapshot
		snapshot.add(\discs -> precess.discsCompact);
		snapshot.add(\sequence -> currentPlayer.sequence.deepCopy);
		dictionary[activeChannels][\presets][12] = snapshot; //temporary storage
	}
}

// Mouse