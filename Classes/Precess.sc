Precess : View {

	var bounds, <userView, timeIndicatorView,
	>strokeWidth, >strokeColor, >discAmount,
	<>clock, <>divisions, <>discs, >hoverBlock, >selectedBlock,
	<>isRunning, <>time=0, <>circleRadius, <>noteClickCallback,
	<>divisionsChangedCallback, <>noteLeftDragCallback, <>noteRightDragCallback,
	<>noteDrawCallback, <>isDrawing;

	*new {
		| parent bounds divisions|
		^super.new.init(parent, bounds, divisions);
	}

	init {
		| parent bounds divisions |
		var padding = 20;
		var mouseDownPoint = nil;

		this.isDrawing = false;
		this.circleRadius = (bounds.width / 2)  - padding;
		this.divisions = divisions;
		this.bounds = bounds;
		this.clock = TempoClock; //or LinkClock();
		strokeColor = Color.black;
		strokeWidth = 1;
		userView = UserView(parent, Rect(0,0,bounds.width, bounds.height));
		this.setup(bounds);

		userView
		.background_(Color.clear)
		.drawFunc_({ |me|
			Pen.width = strokeWidth;
			Pen.strokeColor = Color.white;
			Pen.fillColor = Color.clear;
			discs.do({ arg disc; disc.blocks.do({ arg block; block.draw() }) });
			if (hoverBlock.notNil, { hoverBlock.draw() });
			if (selectedBlock.notNil, {selectedBlock.draw() });
		})
		.mouseDownAction_({ |me,x,y,mod|

			mouseDownPoint = (parent.bounds.left + x)@(parent.bounds.top + y);

			discs.do({ arg disc;
				disc.blocks.do({ arg block;
					var currentBlock = block.containsPoint(x@y);
					if (currentBlock.notNil, {
						selectedBlock = currentBlock;
						noteClickCallback.(currentBlock);
						me.refresh;
					})
				})
			});
			time = clock.bar;
		})
		.mouseUpAction_({ |me,x,y,mod|
			if (selectedBlock.notNil, {
				selectedBlock.isSelected = false;
				selectedBlock = nil;
				me.refresh;
			})
		})
		.mouseMoveAction_({ |me,x,y,mod|

			if( isDrawing, {
				discs.do({ arg disc;
					disc.blocks.do({ arg block;
						var currentBlock = block.containsPoint(x@y);
						if (currentBlock.notNil, {
							if (currentBlock != selectedBlock, {
								selectedBlock = currentBlock;
								noteDrawCallback.(currentBlock);
								me.refresh;
							});
						})
					})
				});

			},{
				var mouse =  (parent.bounds.left + x)@(parent.bounds.top + y);
				if( (mouseDownPoint.x - mouse.x).abs > 3.0, {
					if (mouse.x > mouseDownPoint.x, {
						var value = [mouseDownPoint.x, Window.screenBounds.width].asSpec.unmap(mouse.x);
						noteRightDragCallback.(selectedBlock, value)
					},{
						var value = 1.0 - [0, mouseDownPoint.x].asSpec.unmap(mouse.x);
						noteLeftDragCallback.(selectedBlock, value)
					});
				})
			})
		});

		userView.refresh;

		timeIndicatorView = UserView(parent, Rect(0,0,bounds.width, bounds.height))
		.acceptsMouse_(false)
		.background_(Color.clear)
		.drawFunc_({ |me|
			Pen.width = 2;
			Pen.strokeColor = Color.white.alpha_(0.5);
			Pen.moveTo(me.bounds.center);
			Pen.lineTo((0@0).translate(0@circleRadius).rotate( (time * (pi/2)) - pi).translate(me.bounds.center));
			Pen.stroke;
		});
	}

	setup { |bounds|
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

	start {
		clock.beats = 0;
		time = clock.beats;
		timeIndicatorView.refresh;
		isRunning = true;
		{
			while { isRunning }
			{
				timeIndicatorView.refresh;
				0.05.wait;
				time = clock.beats;
			}
		}.fork(AppClock)
	}

	stop {
		isRunning = false;
	}

	sizeHint {
		^userView.sizeHint;
	}

	minSizeHint {
		^userView.minSizeHint;
	}

	setDiscs_ { |discsCompact|
		var newDivisions = discsCompact.collect({ arg subList; subList.size });
		var discAmount = newDivisions.size;
		var discWidth = circleRadius / discAmount;
		divisions = newDivisions;

		discs = discsCompact.collect({ arg blockList, idx;
			var disc = Disc(idx);
			disc.blocks = blockList.collect({ arg state, segment;
				var block = Block(userView, discWidth, disc, blockList.size, segment);
				block.isOn = state;
			});
		});

		divisionsChangedCallback.(newDivisions);
		userView.refresh;
	}

	discsCompact {
		var noteOns = discs.collect({ arg disc; disc.blocks.collect({ arg block; block.isOn })});
		^noteOns;
	}

	clearBlocks {
		discs.do{ arg disc; disc.blocks.do{ arg block; block.isOn = false } };
		userView.refresh;
	}
}

Disc {
	var <>idx, <>blocks, <>isOn;

	*new { | idx |
		^super.new.init(idx);
	}

	init { | idx |
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
		this.noteEvent =  (time: noteStart, sustain: noteSustain, note: noteDegree);
	}

	nextBlock {
		if (disc.blocks.last == this, { ^disc.blocks.first; }, { ^disc.blocks[segment+1] });
	}

	prevBlock {
		if (disc.blocks.first == this, { ^disc.blocks.last }, { ^disc.blocks[segment-1] });
	}

	draw {
		var startArcAngle =  (startAngle + 1.5pi).wrap(0,2pi);
		startDistance = max(startDistance, 0.0000000000001);
		Pen.addAnnularWedge(parent.bounds.center, startDistance, endDistance, startArcAngle, divisionAngle );

		if (isSelected, {

			//SELECTED + ON
			if (isOn, {
				Pen.fillColor = Color.white;
				Pen.strokeColor = Color.black;
				Pen.width = 4;
				Pen.fillStroke;

				Pen.addAnnularWedge(parent.bounds.center, startDistance, endDistance, startArcAngle, divisionAngle );
			});

			//SELECTED + OFF
			Pen.strokeColor = strokeColor;
			Pen.fillColor = fillColor;
			Pen.width = strokeWidth;
			Pen.fillStroke;

		},{
			//UNSELECTED + ON
			if (isOn, {
				Pen.fillColor = Color.white;
				Pen.strokeColor = Color.black;
				Pen.width = 4;
				Pen.fillStroke;

				Pen.addAnnularWedge(parent.bounds.center, startDistance, endDistance, startArcAngle, divisionAngle );
			});

			//UNSELECTED + OFF
			Pen.fillColor =  Color.clear;
			Pen.strokeColor = Color.white;
			Pen.width = 1;
			Pen.fillStroke;
		});
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