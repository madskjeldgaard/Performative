// Same as above but pdef style
//
/*
(
Pctrldef(\yoyoy, {|ctrl|
    Pbind(\instrument, \default, \dur, 0.25, \degree, ctrl[\degree].trace)
});

Pctrldef(\yoyoy).addParam(\degree, 0, ControlSpec.new(minval:0, maxval:7, warp:\lin, step:1, default:4));
Pctrldef(\yoyoy).play;

r{
    loop{
        1.wait;
        Pctrldef(\yoyoy).map(\degree, rrand(0.0,1.0))
    }
}.play;
)
*/
Pctrldef : Pcontrol{

    var <key;

    classvar <>all;

    *new{ arg key, item;
		var res = this.at(key);
		if(res.isNil) {
			res = super.new(item).prAdd(key);
		} {
			if(item.notNil) { res.source = item }
		}
		^res

	}

    *at{|key|
        ^all[key]
    }

    prAdd { arg argKey;
        key = argKey;
        all.put(argKey, this);
    }

    copy { |toKey|
        if(toKey.isNil or: { key == toKey }) { Error("can only copy to new key (key is %)".format(toKey)).throw };
        ^this.class.new(toKey).copyState(this)
    }

    copyState { |otherPctrldef|
        if(otherPctrldef.patternProxy.source.isNil, {
            "%: no pattern to copy".format(this.class.name).error;
        });

        // this.patternProxy.isNil.if({
        //     this.patternProxy = EventPatternProxy.new(otherPctrldef.patternProxy.source.copy());
        // }, {
        //     this.patternProxy.source = otherPctrldef.patternProxy.source;
        // });


        // this.patternProxy.envir = otherPctrldef.patternProxy.envir.copy;

        this.params = otherPctrldef.params.collect{|param| param.copy()};
        this.source_(otherPctrldef.func.copy());
        this.patternProxy.envir = otherPctrldef.patternProxy.envir.copy();
    }

    // Convenience – copy and immediately change bits of the pattern
    copyChange{ |toKey ... changeKeyValues|
        var newPctrlDef = this.copy(toKey);

        if(changeKeyValues.arePairs, {
            newPctrlDef.change(*changeKeyValues)
        }, {
            "can't set changekeyvalues if not pairs".error;
        });

        ^newPctrlDef
    }

    dup { |n = 2| ^{ this }.dup(n) } // avoid copy in Object::dup

    *hasGlobalDictionary { ^true }

    *initClass {
        all = IdentityDictionary.new;
        Class.initClassTree(Pdef);
    }

    clear{
        patternProxy.clear();
        params.keysValuesDo{|k,v| v.clear()}
    }

    solo{
        all.select({|pdefctrl| pdefctrl != this}).keysValuesDo{|k,v| v.stop};
        this.play;
    }
}

Test_Pctrldef : PerformativeTest {
    var ctrldef;

    setUp {
    }

    tearDown {
        Pctrldef.all[ctrldef.key] = nil;
    }

    test_newctrldef{
        ctrldef = Pctrldef.new(\testctrl, {|ctrl| Pbind() });
        this.assert(ctrldef.key == \testctrl, "Key should be set correctly");
        this.assert(ctrldef.patternProxy.isKindOf(Pattern), "Contains a pattern");
        this.assert(Pctrldef.all[ctrldef.key] == ctrldef, "Should be added to Pctrldef.all");
    }

    test_copyctrldef{
        var ctrldef2;
        ctrldef = Pctrldef.new(\testctrl, {|ctrl| Pbind(\hej, ctrl[\hej], \ho, ctrl[\ho]) });

        // Add params
        ctrldef.addParam(
            \hej, 0.5, \uni,
            \ho, 7, \octave
        );

        ctrldef2 = ctrldef.copy(\testctrl2);

        // Iterate over all params in origin and check if the target has the same param
        ctrldef.params.keysValuesDo{|paramName, param|
            var copiedParam = ctrldef2.params[paramName];
            this.assert(param.source == copiedParam.source, "Copied param % should have the same source".format(paramName));
            this.assert(param.spec == copiedParam.spec, "Copied param % should have the same spec".format(paramName));
            this.assert(param.envir == copiedParam.envir, "Copied param % should have the same envir".format(paramName));
            this.assert(param.pattern == copiedParam.pattern, "Copied param % should have the same pattern".format(paramName));
        };

        this.assert(ctrldef.key == \testctrl, "Origin Key should be the same");
        this.assert(ctrldef.patternProxy.isKindOf(Pattern), "Origin should still contain a pattern");

        this.assert(ctrldef2.key == \testctrl2, "Copy Key should be set correctly");
        this.assert(ctrldef2.patternProxy.isKindOf(Pattern), "Copy should contain a pattern");

        this.assert(ctrldef.patternProxy != ctrldef2.patternProxy, "Origin and copy should not have the same pattern proxy");
        // FIXME: This fails. Should it?
        // this.assert(ctrldef.patternProxy.source == ctrldef2.patternProxy.source, "Origin and copy should have the same pattern source", onFailure: {
        //         "Origin pattern proxy: %".format(ctrldef.patternProxy).postln;
        //         "Copy pattern proxy: %".format(ctrldef2.patternProxy).postln;
        // });

        this.assert(Pctrldef.all[ctrldef.key] == ctrldef, "Origin should still be added to Pctrldef.all");
        this.assert(Pctrldef.all[ctrldef2.key] == ctrldef2, "Copy should be added to Pctrldef.all");

        // Change params and check again
        ctrldef.params.do{|param|
            param.map(rrand(0.0,1.0))
        };

        // Check parameters again
        ctrldef.params.keysValuesDo{|paramName, param|
            var copiedParam = ctrldef2.params[paramName];

            this.assert(param.source != copiedParam.source, "Copied param % should not have the same source".format(paramName));
        };

        // The two patterns' sources should be different now
        this.assert(ctrldef.patternProxy.source != ctrldef2.patternProxy.source, "Origin and copy should not have the same pattern source");

        this.assert(ctrldef != ctrldef2, "Origin and copy should not be equal");
    }

    test_paramMapping{
        ctrldef = Pctrldef.new(\testctrl, {|ctrl| Pbind() });

        // Add params
        ctrldef.addParam(
            \hej, 0.5, \uni,
        );

        ctrldef.map(\hej, 0.0);

        this.assert(ctrldef.params[\hej].source == 0.0, "Param source should be 0.0");

        ctrldef.map(\hej, 1.0);

        this.assert(ctrldef.params[\hej].source == 1.0, "Param source should be 1.0");

        ctrldef.map(\hej, 2.5);

        this.assert(ctrldef.params[\hej].source == 1.0, "Param source should clip");

        ctrldef.map(\hej, -1.0);

        this.assert(ctrldef.params[\hej].source == 0.0, "Param source should clip");

    }

    // Test with multiple params
    test_paramMappingMulti{
        ctrldef = Pctrldef.new(\testctrl, {|ctrl| Pbind() });

    }

}
