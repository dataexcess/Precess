PrecessButton : View  {

	*new {
		| parent bounds|
		^super.new.init(parent, bounds);
	}

	init {
		| parent bounds |
		var numberBox, userView;

		numberBox = NumberBox(parent, bounds)
		.focusColor_(Color.red)
		.clipLo_(1)
		.clipHi_(240)
		.background_(Color.clear)
		.normalColor_(Color.white)
		.stringColor_(Color.white)
		.typingColor_(Color.white)
		.align_(\center)
		.valueAction_(120);

		userView= UserView(parent, bounds)
		.drawFunc_({
			Pen.color = Color.white;
			Pen.width = 2;
			Pen.addRect(Rect(0,0,bounds.width,bounds.height));
			Pen.stroke;
		})
		.acceptsMouse_(false);

		userView.refresh;
	}
}