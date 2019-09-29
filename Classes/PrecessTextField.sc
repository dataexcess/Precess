PrecessTextField : View  {

	var <>textField;

	*new {
		| parent bounds|
		^super.new.init(parent, bounds);
	}

	init {
		| parent bounds |
		var userView;

		textField = TextField(parent, bounds)
		.background_(Color.clear)
		.stringColor_(Color.white)
		.align_(\center)
		.valueAction_("2,4,9,16,22,64");
		// .canFocus_(false);

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

	sizeHint {
		^textField.sizeHint;
	}

	minSizeHint {
		^textField.minSizeHint;
	}
}