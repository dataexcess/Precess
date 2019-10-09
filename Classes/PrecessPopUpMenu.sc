PrecessPopUpMenu : View  {

	var <>popUpMenu;

	*new {
		| parent bounds|
		^super.new.init(parent, bounds);
	}

	init {
		| parent bounds |
		var userView;

		popUpMenu = PopUpMenu(parent, bounds)
		.background_(Color.clear)
		.stringColor_(Color.white);

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
		^popUpMenu.sizeHint;
	}

	minSizeHint {
		^popUpMenu.minSizeHint;
	}
}