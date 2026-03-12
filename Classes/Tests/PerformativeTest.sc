PerformativeTest1 : UnitTest {
	test_check_classname {
		var result = Performative.new;
		this.assert(result.class == Performative);
	}
}


PerformativeTester {
	*new {
		^super.new.init();
	}

	init {
		PerformativeTest1.run;
	}
}
