Precess {

	var bounds, userView, timeIndicatorView,
	>strokeWidth, >strokeColor,
	>discs, >discAmount,
	>divisions, <>discs, >hoverBlock, >selectedBlock,
	<>player, <>isRunning, <>time=0;

	*new {
		| parent bounds divisions player|
		^super.new.init(parent, bounds, divisions, player);
	}

	init {
		| parent bounds divisions player|
		var padding = 20;
		var circleRadius = (bounds.width / 2)  - padding;
		this.player = player ??  RealTimeEventStreamPlayer();
		this.divisions = divisions;
		strokeColor = Color.black;
		strokeWidth = 1;

		userView = UserView(parent, Rect(0,0, bounds.width, bounds.height));
		this.setup(bounds);

		userView
		.background_(Color.black)
		.drawFunc_({ |me|
			Pen.width = strokeWidth;
			Pen.strokeColor = Color.white;
			Pen.fillColor = Color.clear;
			discs.do({ arg disc; disc.blocks.do({ arg block; block.draw() }) });
			if (hoverBlock.notNil, { hoverBlock.draw() });
			if (selectedBlock.notNil, {selectedBlock.draw() });
		})
		.mouseOverAction_({ |me,x,y,mod|

			/*			hoverBlock = nil;

			discs.do({ arg disc;
			disc.blocks.do({ arg block;
			var newHoverBlock = block.containsPoint(x@y);

			if (hoverBlock.notNil, {
			if (newHoverBlock.notNil, {
			if (hoverBlock !== newHoverBlock, {
			hoverBlock = newHoverBlock;
			me.refresh;
			})
			})
			}, {
			if (newHoverBlock.notNil, {
			hoverBlock = newHoverBlock;
			});
			me.refresh;
			})
			});
			});

			this.updateTimeIndicator();*/

		})
		.mouseDownAction_({ |me,x,y,mod|

			discs.do({ arg disc;
				disc.blocks.do({ arg block;

					var currentBlock = block.containsPoint(x@y);
					if (currentBlock.notNil, {
						//SELECED BLOCK
						selectedBlock = currentBlock;

						if (currentBlock.isOn, {

							//NOTE WAS ON
							currentBlock.isOn = false;
							player.deleteNote(currentBlock.noteEvent[\time], currentBlock.noteEvent[\degree]);
						},{
							//NOTE WAS OFF
							currentBlock.isOn = true;
							currentBlock.noteEvent;
							player.insertNote(currentBlock.noteEvent[\time], currentBlock.noteEvent[\degree]);
						});

						me.refresh;
					})
				})
			});
			this.updateTimeIndicator();
		})
		.mouseUpAction_({ |me,x,y,mod|
			selectedBlock.isSelected = false;
			selectedBlock = nil;
			me.refresh;
		})
		.mouseMoveAction_({ |me,x,y,mod|

			discs.do({ arg disc;
				disc.blocks.do({ arg block;

					/*var currentBlock = block.containsPoint(x@y);
					if (currentBlock.notNil, {
					currentBlock.disc.blocks.do({ arg block; block.isOn = 0.5.coin});
					me.refresh;
					})*/
				})
			});

			//this.updateTimeIndicator();
		});

		userView.refresh;

		timeIndicatorView = UserView(parent, Rect(0,0, bounds.width, bounds.height))
		.acceptsMouse_(false)
		.background_(Color.clear)
		.drawFunc_({ |me|
			Pen.width = 2;
			Pen.strokeColor = Color.white.alpha_(0.5);
			Pen.moveTo(me.bounds.center);
			Pen.lineTo((0@0).translate(0@circleRadius).rotate( (time * (pi/2)) - pi).translate(me.bounds.center));
			Pen.stroke;
		});

		isRunning = true;

		time = player.getBeats();
		{
			while { isRunning }
			{
				timeIndicatorView.refresh;
				0.05.wait;
				time = player.getBeats();
			}
		}.fork(AppClock)
	}

	updateTimeIndicator {
		time = player.getBeats();
		timeIndicatorView.refresh;
	}

	setup { |bounds|
		var padding = 20;
		var circleRadius = (bounds.width / 2)  - padding;
		var discAmount = divisions.size;
		var discWidth = circleRadius / discAmount;

		discs = List();
		divisions.do({ arg division, idx;
			var blocks = List();
			var disc = Disc(idx);

			division.do({ arg segment;
				var block = Block(userView, discWidth, disc, division, segment);
				blocks.add(block);
			});

			disc.blocks = blocks;
			discs.add(disc);
		});
	}
}

Disc {
	var <>idx, <>blocks, <>isOn;

	*new {
		| idx |
		^super.new.init(idx);
	}

	init {
		| idx |
		this.idx = idx;
	}
}

Block {
	var >parent, <>disc, <>divisions, >segment, >strokeWidth, >strokeColor, >fillColor, >divisionAngle, <>startAngle, >endAngle, >startDistance, >endDistance, <>isOn, <>isSelected, <>noteStart, <>noteSustain, <>noteDegree, <>noteEvent;

	*new {
		| parent width disc divisions segment |
		^super.new.init(parent, width, disc, divisions, segment);
	}

	init {
		| parent width disc divisions segment |
		this.parent = parent;
		this.disc = disc;
		this.divisions = divisions;
		this.segment = segment;
		this.divisionAngle = (2pi / divisions);
		this.startAngle =   divisionAngle * segment;
		this.endAngle = divisionAngle * (segment + 1);
		this.startDistance = width * disc.idx;
		this.endDistance = width * (disc.idx + 1);
		this.strokeColor = Color.white;
		this.fillColor = Color.clear;
		this.strokeWidth = 1;
		this.isOn = false;
		this.isSelected = false;
		this.noteStart =  (startAngle /2pi) * 4;
		this.noteSustain = (1/divisions) * 4;
		this.noteDegree = 1;
		this.noteEvent =  ( time: noteStart, sustain: noteSustain, degree: noteDegree);
	}

	draw {
		var startArcAngle =  (startAngle + 1.5pi).wrap(0,2pi);
		Pen.addAnnularWedge(parent.bounds.center, startDistance, endDistance, startArcAngle, divisionAngle );

		if (isSelected, {
			//SELECTED + OFF
			strokeWidth = 3;
			strokeColor = Color.red;
			fillColor = Color.white.alpha_(0.1);

			if (isOn, {
				//SELECTED + ON
				strokeColor = Color.red;
				fillColor = Color.grey;
			})
		},{
			//UNSELECTED + OFF
			strokeWidth = 1;
			strokeColor = Color.white;
			fillColor = Color.clear;

			if (isOn, {
				//UNSELECTED + ON
				strokeColor = Color.white;
				fillColor = Color.grey;
			})
		});

		Pen.strokeColor = strokeColor;
		Pen.fillColor = fillColor;
		Pen.width = strokeWidth;
		Pen.fillStroke;
	}

	containsPoint { | mouse |

		var inverseCenter = Point(parent.bounds.center.x.neg,parent.bounds.center.y.neg);
		var translated = mouse.translate(inverseCenter);
		var final = Point(translated.x, translated.y);
		var angle = [0,2pi].asSpec.map([-pi,pi].asSpec.unmap(final.theta)); //weird mapping between point.theta and Pen.addArc
		var finaAngle = (angle - (pi/2)).wrap(0,2pi);
		var distance = abs(mouse.dist(parent.bounds.center));

		if ( (finaAngle > startAngle) && (finaAngle < endAngle) && (distance > startDistance) && (distance < endDistance), {
			isSelected = true;
			^this
		}, {
			isSelected = false;
			^nil;
		})
	}
}
