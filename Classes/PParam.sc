// The same as a pattern proxy (pdefn) but with a spec and mapping capabilities;
/*
(
p = Pparam.new(500, Spec.specs[\freq]);
p.map(0.2);
p.source.postln
)

// Using arrayed spec !
(
Pctrldef(\yo)
.addParam(
    \myArrayParam, \hey, [\hey, \yo, \ho]
);

Pctrldef(\yo)[\myArrayParam].source.postln;
Pctrldef(\yo).map(\myArrayParam, 0.5);
Pctrldef(\yo)[\myArrayParam].source.postln;
)


*/
Pparam : PatternProxy{
    var <param;

    *new{|source, controlspec|
        ^super.new().source_(source).init(source, controlspec)
    }

    init{|source, controlspec|
        this.source = source;
        param = ParamFunc.new({|mapped|
            this.source = mapped;
        }, controlspec);
    }

    copy{
        ^this.class.new(this.source, this.param.spec).envir_(this.envir.copy)
    }

	copyState { |proxy|
		envir = proxy.envir.copy;
		this.source = proxy.source;
        this.param = proxy.param.copy;
	}

    spec_{|newSpec|
        param.spec_(newSpec);
    }

    spec{
        ^param.spec
    }

    set{|value|
        param.set(value);
    }

    value{
        ^this.source;
    }

    // Uses a spec to map it's values (yes, I know, it overwrites original map)
    map{|value|
        param.map(value);
    }

    getUnmapped{
        ^param.getUnmapped
    }

    randomize{
        param.randomize;
    }

    gui{
        ^param.gui;
    }
}

PparamDef : Pparam{
    var <key;
    classvar <>all;

    *new{ arg key, item, controlspec;
        var res = this.at(key);
        if(res.isNil) {
            res = super.new(item, controlspec).prAdd(key);
        } {
            if(item.notNil) { res.source = item }
        }
        ^res

    }

    *at{|key|
        ^all[key]
    }

    *hasGlobalDictionary { ^true }

    *initClass {
        all = IdentityDictionary.new;
        Class.initClassTree(Pdef);
    }

    prAdd { arg argKey;
        key = argKey;
        all.put(argKey, this);
    }

    copy { |toKey|
        if(toKey.isNil or: { key == toKey }) { Error("can only copy to new key (key is %)".format(toKey)).throw };
        ^this.class.new(toKey).copyState(this)
    }
}

TestPparam : PerformativeTest {

    setUp {
        // Called before each test
    }

    tearDown {
        // Called after each test
    }

    test_setAndGet {
        var spec = Spec.specs[\freq];
        var p = Pparam.new(500, spec);
        this.assertEquals(p.value, 500, "Initial value should be 500. Got %".format(p.value));
        p.set(600);
        this.assertEquals(p.value, 600, "After setting to 600, value should be 600. Got %".format(p.value));
    }

    test_mapping {
        var spec = Spec.specs[\freq];
        var p = Pparam.new(500, spec);
        var mapVal = 0.2;
        var expected = spec.map(mapVal);
        p.map(mapVal);
        this.assertEquals(p.value, expected, "Mapped value should be %. Got %".format(expected, p.value).postln);
    }

    test_randomize {
        var spec = Spec.specs[\freq];
        var p = Pparam.new(500, spec);
        p.randomize;
        this.assert(p.value >= spec.minval and: { p.value <= spec.maxval }, "Randomized value should be within spec range");
    }

    test_unmapped {
        var spec = Spec.specs[\freq];
        var p = Pparam.new(500, spec);
        var mapVal = 0.25;
        var expected, actual;

        p.map(mapVal);
        expected = spec.unmap(spec.map(mapVal));
        actual = p.getUnmapped;
        this.assertFloatEquals(actual, expected, "Unmapped value should be close to original map value %. Got: %".format(expected, actual));
    }


    test_copypparam {
        var param = Pparam.new(10, Spec.specs[\freq]);
        var copy = param.copy();

        // Check if envir is the same
        this.assert(copy.envir == param.envir, "After copying, envir should be the same");

        // Check if pattern is the same
        this.assert(copy.pattern == param.pattern, "After copying, pattern should be the same");

        // Change source
        param.source = 5;
        this.assert(copy.source != param.source, "After changing source, copy should not be equal to original");

        // Change spec
        param.param.spec = [0,5];
        this.assert(copy.param.spec != param.param.spec, "After changing spec, copy should not be equal to original");

        // And finally check that they aren't equal
        this.assert(copy != param, "After changing source and spec, copy should not be equal to original");
    }
}

+Pparam{
    asView{|parent, bounds, font, key="Parameter"|
        ^this.param.asView(parent, bounds, font, key)
    }
}
