(

w = Window("Precess", Rect(20,20,600,600)).front.acceptsMouseOver_(true);
~realTimePlayer = RealTimeEventStreamPlayer();
~precess = Precess(w,w.bounds,[1,3,7,8,16,18,64],~realTimePlayer);
TempoClock.tempo_(120/60)

)
