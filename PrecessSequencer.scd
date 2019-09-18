(
w = Window("Precess", Rect(20,20,600,600)).front.acceptsMouseOver_(true);

s.latency = 0.07;

MIDIClient.init(0,1);
~midiOut =  MIDIOut(0, MIDIClient.destinations[1].uid);
~realTimePlayer = RealTimeEventStreamPlayer();
~realTimePlayer.synthDef = \test_synth;
//~realTimePlayer.midiOut = ~midiOut; //uncomment this line for MIDI OUTPUT on device 1 channel 1 instead of \testSynth

~timeDivisions = [1,2,4,7,8,16,64];

~precess = Precess(w, w.bounds, ~timeDivisions, ~realTimePlayer);
~precess.initialiseWithFirstQuarterNote;

SynthDef(\test_synth, { |out=0, freq=440, amp=8.0, pan=0, gate=1, ratio = 1, index = 1, ar = 0.1, dr = 0.1|
	var env = Env.adsr(0.02, 0.1, amp, 0.01); //sustained
	//var env = Env.perc(releaseTime: 0.1); //decayed
	var envGen = EnvGen.kr(env, gate, doneAction: Done.freeSelf);
	var z = PMOsc.ar(freq, freq * ratio, Linen.kr(gate, ar,index, dr), 0, 0.3)  * envGen;
	Out.ar(0, Pan2.ar(z, pan, amp));
}).add;

TempoClock.tempo_(120/60);
)