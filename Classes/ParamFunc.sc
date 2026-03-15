// ParamFunc and ParamFuncSet are classes for managing parameters with associated callback functions and specs. They allow you to define parameters that automatically call a function when their value changes, and to manage collections of such parameters with the ability to save and restore snapshots of their states.

// It's basically the same as Pparam, but instead of a pattern it contains a function that is called when the parameter changes.
/*

(
p = ParamFunc.new(
    {|mapped, raw|
        "New values. Mapped to spec range: %, raw: %".format(mapped,raw).postln
    },
    [10.0,20000.0,\exp].asSpec
);

p.set(0.1);
p.set(0.25);
p.set(0.5);
p.set(0.75);
p.randomize();
)

Also works with ArrayedSpecs:

(
p = ParamFunc.new(
    {|mapped, raw|
        "New values. Mapped to spec range: %, raw: %".format(mapped,raw).postln
    },
    [\one, \two, \three]
);

p.set(0.1);
p.set(0.25);
p.set(0.5);
p.set(0.75);
p.randomize()
)

// With list of specs
(
var specList = [\freq.asSpec, \midinote.asSpec, [0,10].asSpec];
p = ParamFunc.new(
    {|mapped, raw|
        "New values. Mapped to spec range: %, raw: %".format(mapped,raw).postln
    },
    specList
);

p.set([0.5, 0.25, 0.85]);
p.randomize();
)
*/
// FIXME: Only call callback if value actually changes?
ParamFunc {
    var <func, <spec, <source, <normalizedValue;
    var <isListOfSpecs = false;
    var <controlMode;
    var changeCallback; // Callback when it is changed

    // If controlspec is an array, an ArrayedSpec is automatically created from it.
    *new { |func, controlspec|
        ^super.new().init(func, controlspec)
    }

    init { |func, controlspec|
        this.func_(func);
        this.spec_(controlspec);
        changeCallback = {};
    }

    changeCallback_ { |newFunc|
        changeCallback = newFunc;
    }

    func_ {|newFunc|
        func = newFunc
    }

    changed{
        if(changeCallback.notNil) {
            changeCallback.value(this);
        }
    }

    // Three types of specs are allowed:
    // 1. Spec/ControlSpec/ArrayedSpec
    // 2. An array which will be converted to an ArraySpec, unless it's 3.
    // 3. An array of ControlSpecs to allow mapping values using arrays, for complex one to many mappings of parameters
    spec_ { |newSpec|
        var isSpec = newSpec.isKindOf(Spec);
        var isArray = newSpec.isKindOf(SequenceableCollection);
        var isArraySpec = newSpec.isKindOf(ArrayedSpec);
        isListOfSpecs = if(isArray, {
            newSpec.every{|item|
                item.isKindOf(ControlSpec) or: {
                    item.isKindOf(ArrayedSpec)
                }
            }
        });

        controlMode = nil;
        controlMode = case
            { isArray && isListOfSpecs} { \specList }
            { isArray || isArraySpec } { \arrayspec }
            { isSpec } {\spec};

        if(controlMode.isNil, {
            "ParamFunc: Illegal type of spec: %. \nShould be a type of Spec, an array (automatically converted to ArraySpec), ArraySpec or an array of ControlSpecs".format(newSpec.class.asString).error
        });

        spec = switch(controlMode,
            \spec, {
                newSpec
            },
            \arrayspec, {
                newSpec.asArrayedSpec()
            },
            \specList, {
                newSpec
            }
        );

        source = if(controlMode != \specList, {spec.default}, {
            // Special case: specList, it's a list of control specs
            spec.collect{|specItem| specItem.default }
        });

        this.changed();
    }

    setRaw { |value|
        source = value;
        normalizedValue = if(controlMode != \specList, {spec.unmap(value)}, {
            // Special case: specList, it's a list of control specs
            spec.collect{|specItem| specItem.unmap(value) }
        });
        func.value(value, value); // raw value for both

        this.changed();
    }

    set { |value|
        normalizedValue = value;

        if (spec.notNil) {
            if(controlMode != \specList, {
                var mapped = spec.map(value);
                source = mapped;
                func.value(mapped, value); // mapped and raw
            }, {
                if(value.size != spec.size, {
                    "ParamFunc: Value array should be same size as speclist array".error;
                }, {
                    // Special case: specList, it's a list of control specs
                    var mapped = spec.collect{|specItem, index| specItem.map(value[index])};
                    source = mapped;
                    func.value(mapped, value); // mapped and raw
                })
            });
        } {
            var mapped = \uni.asSpec.map(value);
            "No spec found for %. Using unipolar".format(this.class.name).warn;
            source = mapped;
            func.value(mapped, value);
        };

        this.changed();
    }

    // Manually trigger callback with latest values
    // update{ }

    value {
        ^source;
    }

    map { |value|
        this.set(value);
    }

    getUnmapped {
        ^spec.unmap(source);
    }

    randomize {
        if(spec.notNil, {
            if(controlMode != \specList, {
                source = spec.randomValue;
            }, {
                source = spec.collect{|specItem| specItem.randomValue };
            });
            func.value(source, source); // mapped and raw are the same in this case
        }, {
            "No spec found for %.".format(this.class.name).error;
        });

            this.changed();

    }
}

// A collection of ParamFuncs with the ability to save/restore snapshots
/*

p = ParamFuncSet();
p.add(\freq, {|mapped, raw| "Freq mapped: %, raw: %".format(mapped, raw).postln}, [10.0, 20000.0, \exp].asSpec);
p[\freq].set(0.5);

// More advanced:
(
p = ParamFuncSet();
p.add(\freq, {|mapped, raw| "Freq mapped: %, raw: %".format(mapped, raw).postln}, [10.0, 20000.0, \exp].asSpec);
p.add(\amp, {|mapped, raw| "Amp mapped: %, raw: %".format(mapped, raw).postln}, \amp.asSpec);
p.add(\yoyoy, {|mapped, raw| "Yoyo mapped: %, raw: %".format(mapped, raw).postln}, [\hey, \yo, \hi]);
p.add(\specsss, {|mapped, raw| "Specsss mapped: %, raw: %".format(mapped, raw).postln}, [[0,1].asSpec, \cutoff.asSpec]);
)

p.randomizeAll()
p.snapshot(\one)

p.snapshots[\one]

p.randomizeAll()
p.snapshot(\two)

p.restoreSnapshot(\one)

p[\yoyoy].setRaw(\hey);

p[\freq].set(0.5);

*/
ParamFuncSet[]{
    var <all;
    var <snapshots;
    var changeCallback; // Callback when any ParamFunc in the set changes

    *new {
        ^super.new().init;
    }

    init {
        all = IdentityDictionary.new;
        snapshots = IdentityDictionary.new;
        changeCallback = {};
    }

    applyAll {
        all.keysValuesDo{ |name, paramFunc|
            var currentValue = paramFunc.value;
            paramFunc.setRaw(currentValue);
        }
    }

    changed {
        if(changeCallback.notNil) {
            changeCallback.value(this);
        }
    }

    changeCallback_ { |newFunc|
        changeCallback = newFunc;
    }

    add { |key, func, controlspec|
        var newFunc, currentValue;
        if(all[key].notNil) {
            // "ParamFuncSet: Key % already exists. Removing.".warn;
            this.remove(key);
        };

        newFunc = ParamFunc.new(func, controlspec)
        .changeCallback_({ this.changed });

        all.put(key, newFunc);

        // Call Paramfunc
        newFunc.setRaw(newFunc.value);

        this.changed();

        ^newFunc;
    }

    remove { |key|
        all.removeAt(key);

        this.changed();
    }

    at { |key|
        ^all[key];
    }

    // Randomize all except the keys in except
    randomizeAll {|...except|
        all.keysValuesDo{ |key, paramFunc|
            if(except.notNil and: { except.contains(key) }) {} {
                paramFunc.randomize;
            }
        };

        this.changed();
    }

    getSnapshotNames {
        ^snapshots.keys.asArray;
    }

    snapshot{|name|
        // Each snapshot saves the current state of all ParamFuncs in the set
        var newSnapshot = IdentityDictionary.new;
        all.keysValuesDo{ |key, paramFunc|
            var val = paramFunc.value;
            "Putting key: %, normalized value: % in snapshot".format(key, val).postln;
            newSnapshot.put(key, val);
        };
        snapshots.put(name ?? {Date.getDate.stamp}, newSnapshot);
        this.changed();
    }

    removeSnapshot{|name|
        snapshots.removeAt(name);
        this.changed();
    }

    restoreSnapshot{|name|
        var snapshot = snapshots[name];
        if(snapshot.isNil) {
            "ParamFuncSet: No snapshot found with name %".format(name).error;
        } {
            snapshot.keysValuesDo{ |key, value|
                var paramFunc = all[key];

                if(paramFunc.notNil) {
                    "%, key: %, value: %".format(paramFunc, key, value).postln;
                    paramFunc.setRaw(value);
                } {
                    "ParamFuncSet: No ParamFunc found for key % in snapshot %".format(key, name).warn;
                }
            }
        };

        this.changed();
    }


}

TestParamFunc : PerformativeTest {

    setUp {
        // Called before each test
    }

    tearDown {
        // Called after each test
    }

    test_changeCallbacks {
        var pfs;
        var called = false;
        var pf = ParamFunc({|mapped, raw| }, [0, 10].asSpec);
        pf.changeCallback_({|paramFunc| "Change callback called with value: %".format(paramFunc.value).postln; called = true; });
        pf.set(0.5);
        this.assert(called, "Change callback should be called when value changes");

        // Test with the set
        called = false;
        pfs = ParamFuncSet();
        pfs.changeCallback_({|paramFuncSet| "ParamFuncSet change callback called".postln; called = true; });
        pfs.add(\test, {|mapped, raw| }, [0, 1].asSpec);
        pfs.at(\test).set(0.5);
        this.assert(called, "ParamFuncSet change callback should be called when a ParamFunc changes");
    }

    test_defaultValue {
        var pf, pf2, pf3;

        // For spec value
        pf = ParamFunc({|mapped, raw| }, [3, 10].asSpec);
        this.assertEquals(pf.value, 3, "Default value should be the minval of the spec");

        // For arrayed spec value
        pf2 = ParamFunc({|mapped, raw| }, [\one, \two, \three]);
        this.assertEquals(pf2.value, \one, "Default value should be the first element of the arrayed spec");

        // For speclist
        pf3 = ParamFunc({|mapped, raw| }, [\freq.asSpec, \amp.asSpec]);
        this.assert(pf3.value.isArray, "Value should be an array");
        this.assertEquals(pf3.value.size, 2, "Array size should be 2");
        this.assertFloatEquals(pf3.value[0], \freq.asSpec.default, "First default value should match freq spec default");
        this.assertFloatEquals(pf3.value[1], \amp.asSpec.default, "Second default value should match amp spec default");
    }

    test_basicSpecMapping {
        var called = false;
        var mappedVal, rawVal;
        var pf = ParamFunc({|mapped, raw|
            called = true;
            mappedVal = mapped;
            rawVal = raw;
        }, [0, 10].asSpec);

        pf.set(0.5);
        this.assert(called, "Callback should be called");
        this.assertEquals(pf.value, 5, "Mapped value should be 5");
        this.assertEquals(mappedVal, 5, "Callback mapped value should be 5");
        this.assertEquals(rawVal, 0.5, "Callback raw value should be 0.5");
        this.assertFloatEquals(pf.getUnmapped, 0.5, "Unmapped value should be 0.5");
    }

    test_setRaw {
        var pf = ParamFunc({|mapped, raw| }, [0, 10].asSpec);
        pf.setRaw(7);
        this.assertEquals(pf.value, 7, "Raw value should be 7");
        this.assertFloatEquals(pf.getUnmapped, 0.7, "Unmapped value should be 0.7");
    }

    test_arrayedSpec {
        var pf = ParamFunc({|mapped, raw| }, [\hey, \ho, \yo]);
        pf.set(1.0);
        this.assertEquals(pf.value, \yo, "Value should be yo. Got: %".format(pf.value));
    }

    test_specList {
        var pf = ParamFunc({|mapped, raw| }, [\freq.asSpec, \amp.asSpec]);
        pf.set([0.5, 0.5]);
        this.assert(pf.value.isArray, "Value should be an array");
        this.assertEquals(pf.value.size, 2, "Array size should be 2");
        this.assertFloatEquals(pf.value[0], \freq.asSpec.map(0.5), "First mapped value should match freq spec");
        this.assertFloatEquals(pf.value[1], \amp.asSpec.map(0.5), "Second mapped value should match amp spec");
    }

    test_randomize {
        var pf = ParamFunc({|mapped, raw| }, [0, 1].asSpec);
        pf.randomize;
        this.assert(pf.value >= 0 and: { pf.value <= 1 }, "Randomized value should be in range");
    }

    test_ParamFuncSet_add_and_at {
        var pfs = ParamFuncSet();
        pfs.add(\freq, {|mapped, raw| }, [10, 1000, \exp].asSpec);
        this.assert(pfs.at(\freq).notNil, "ParamFuncSet should contain freq key");
    }


    test_snapshot_and_restore {
        var pfs = ParamFuncSet();
        var freqSpec = [10, 1000, \exp].asSpec;
        var ampSpec = \amp.asSpec;
        pfs.add(\freq, {|mapped, raw| }, freqSpec);
        pfs.add(\amp, {|mapped, raw| }, ampSpec);
        pfs.at(\freq).set(0.1);
        pfs.at(\amp).set(0.9);
        pfs.snapshot(\snap1);
        pfs.at(\freq).set(0.9);
        pfs.at(\amp).set(0.1);
        pfs.restoreSnapshot(\snap1);
        this.assertFloatEquals(pfs.at(\freq).getUnmapped, 0.1, "Restored freq should be 0.1");
        this.assertFloatEquals(pfs.at(\freq).value, freqSpec.map(0.1), "Restored freq should be mapped to spec");
        this.assertFloatEquals(pfs.at(\amp).getUnmapped, 0.9, "Restored amp should be 0.9");
        this.assertFloatEquals(pfs.at(\amp).value, ampSpec.map(0.9), "Restored amp should be mapped to spec");
    }

    test_remove_and_removeSnapshot {
        var pfs = ParamFuncSet();
        pfs.add(\freq, {|mapped, raw| }, [10, 1000, \exp].asSpec);
        pfs.snapshot(\snap1);
        pfs.remove(\freq);
        this.assert(pfs.at(\freq).isNil, "Removed key should be nil");
        pfs.removeSnapshot(\snap1);
        this.assert(pfs.snapshots[\snap1].isNil, "Removed snapshot should be nil");
    }
}
