// ParamFunc and ParamFuncSet are classes for managing parameters with associated callback functions and specs. They allow you to define parameters that automatically call a function when their value changes, and to manage collections of such parameters with the ability to save and restore snapshots of their states.
ParamFunc {
    var <func, <spec, <source, <normalizedValue;
    var <isListOfSpecs = false;
    var <locked = false;
    var <controlMode;
    var <lastRawValue;
    var <changeCallback; // Callback when it is changed

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
        var isSymbol = newSpec.isKindOf(Symbol);
        var isSpec = newSpec.isKindOf(Spec) or: isSymbol;
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
                isSymbol.if({
                    newSpec = newSpec.asSpec
                });
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

    set { |value|
      if(locked.not, {
        source = value;
        normalizedValue = if(controlMode != \specList, {spec.unmap(value)}, {
          // Special case: specList, it's a list of control specs
          spec.collect{|specItem| specItem.unmap(value) }
        });

        if(value != lastRawValue) {
            func.value(value, this); // raw value for both
            lastRawValue = value;
        };

      this.changed();
      })
    }

    map { |value|
      if(locked.not, {
        normalizedValue = value;

        if (spec.notNil) {
            if(controlMode != \specList, {
                var mapped = spec.map(value);
                source = mapped;
                if(mapped != lastRawValue) {
                    func.value(mapped, this); // mapped and raw
                    lastRawValue = mapped;
                };
            }, {
                if(value.size != spec.size, {
                    "ParamFunc: Value array should be same size as speclist array".error;
                }, {
                    // Special case: specList, it's a list of control specs
                    var mapped = spec.collect{|specItem, index| specItem.map(value[index])};
                    source = mapped;
                    if(mapped != lastRawValue) {
                        func.value(mapped, this); // mapped and raw
                        lastRawValue = mapped;
                    };
                })
            });
        } {
            var mapped = \uni.asSpec.map(value);
            "No spec found for %. Using unipolar".format(this.class.name).warn;
            source = mapped;
            if(mapped != lastRawValue) {
                func.value(mapped, this);
                lastRawValue = mapped;
            }
        };

        this.changed();
      })
    }

    // Manually trigger callback with latest values
    // update{ }

    value {
        ^source;
    }

    getUnmapped {
        ^spec.unmap(source);
    }

    unmap{|value|
        ^spec.unmap(value);
    }

    lock{|lockParam=true|
      locked = lockParam;
      this.changed();
    }

    randomize {
        if(locked.not, {

            if(spec.notNil, {
                if(controlMode != \specList, {
                    source = spec.randomValue;
                }, {
                    source = spec.collect{|specItem| specItem.randomValue };
                });
                if(source != lastRawValue) {
                    func.value(source, this); // mapped and raw are the same in this case
                    lastRawValue = source;
                };
            }, {
                "No spec found for %.".format(this.class.name).error;
            });

            this.changed();
        })
    }
}

TestParamFunc : PerformativeTest {

    setUp {
        // Called before each test
    }

    tearDown {
        // Called after each test
    }

    test_lock{
        var pf = ParamFunc({|mapped, obj| }, [0, 10].asSpec);
        pf.lock(true);
        pf.map(0.5);
        this.assertEquals(pf.value, 0, "Value should not change when locked");
        pf.lock(false);
        pf.map(0.5);
        this.assertEquals(pf.value, 5, "Value should change when unlocked");
    }

    test_changeCallbacks {
        var pfs;
        var called = false;
        var pf = ParamFunc({|mapped, obj| }, [0, 10].asSpec);
        pf.changeCallback_({|paramFunc| "Change callback called with value: %".format(paramFunc.value).postln; called = true; });
        pf.map(0.5);
        this.assert(called, "Change callback should be called when value changes");

        // Test with the set
        called = false;
        pfs = ParamFuncSet();
        pfs.changeCallback_({|paramFuncSet| "ParamFuncSet change callback called".postln; called = true; });
        pfs.add(\test, {|mapped, obj| }, [0, 1].asSpec);
        pfs.at(\test).set(0.5);
        this.assert(called, "ParamFuncSet change callback should be called when a ParamFunc changes");
    }

    test_defaultValue {
        var pf, pf2, pf3;

        // For spec value
        pf = ParamFunc({|mapped, obj| }, [3, 10].asSpec);
        this.assertEquals(pf.value, 3, "Default value should be the minval of the spec");

        // For arrayed spec value
        pf2 = ParamFunc({|mapped, obj| }, [\one, \two, \three]);
        this.assertEquals(pf2.value, \one, "Default value should be the first element of the arrayed spec");

        // For speclist
        pf3 = ParamFunc({|mapped, obj| }, [\freq.asSpec, \amp.asSpec]);
        this.assert(pf3.value.isArray, "Value should be an array");
        this.assertEquals(pf3.value.size, 2, "Array size should be 2");
        this.assertFloatEquals(pf3.value[0], \freq.asSpec.default, "First default value should match freq spec default");
        this.assertFloatEquals(pf3.value[1], \amp.asSpec.default, "Second default value should match amp spec default");
    }

    test_basicSpecMapping {
        var called = false;
        var mappedVal, objVal;
        var pf = ParamFunc({|mapped, obj|
            called = true;
            mappedVal = mapped;
        }, [0, 10].asSpec);

        pf.map(0.5);
        this.assert(called, "Callback should be called");
        this.assertEquals(pf.value, 5, "Mapped value should be 5");
        this.assertEquals(mappedVal, 5, "Callback mapped value should be 5");
        this.assertFloatEquals(pf.getUnmapped, 0.5, "Unmapped value should be 0.5");
    }

    test_set {
        var pf = ParamFunc({|mapped, obj| }, [0, 10].asSpec);
        pf.set(7);
        this.assertEquals(pf.value, 7, "Raw value should be 7");
        this.assertFloatEquals(pf.getUnmapped, 0.7, "Unmapped value should be 0.7");
    }

    test_arrayedSpec {
        var pf = ParamFunc({|mapped, obj| }, [\hey, \ho, \yo]);
        pf.map(1.0);
        this.assertEquals(pf.value, \yo, "Value should be yo. Got: %".format(pf.value));
    }

    test_specList {
        var pf = ParamFunc({|mapped, obj| }, [\freq.asSpec, \amp.asSpec]);
        pf.map([0.5, 0.5]);
        this.assert(pf.value.isArray, "Value should be an array");
        this.assertEquals(pf.value.size, 2, "Array size should be 2");
        this.assertFloatEquals(pf.value[0], \freq.asSpec.map(0.5), "First mapped value should match freq spec");
        this.assertFloatEquals(pf.value[1], \amp.asSpec.map(0.5), "Second mapped value should match amp spec");
    }

    test_randomize {
        var pf = ParamFunc({|mapped, obj| }, [0, 1].asSpec);
        pf.randomize;
        this.assert(pf.value >= 0 and: { pf.value <= 1 }, "Randomized value should be in range");
    }

    test_ParamFuncSet_add_and_at {
        var pfs = ParamFuncSet();
        pfs.add(\freq, {|mapped, obj| }, [10, 1000, \exp].asSpec);
        this.assert(pfs.at(\freq).notNil, "ParamFuncSet should contain freq key");
    }


    test_snapshot_and_restore {
        var pfs = ParamFuncSet();
        var freqSpec = [10, 1000, \exp].asSpec;
        var ampSpec = \amp.asSpec;
        pfs.add(\freq, {|mapped, obj| }, freqSpec);
        pfs.add(\amp, {|mapped, obj| }, ampSpec);
        pfs.at(\freq).map(0.1);
        pfs.at(\amp).map(0.9);
        pfs.snapshot(\snap1);
        pfs.at(\freq).map(0.9);
        pfs.at(\amp).map(0.1);
        pfs.restoreSnapshot(\snap1);
        this.assertFloatEquals(pfs.at(\freq).getUnmapped, 0.1, "Restored freq should be 0.1");
        this.assertFloatEquals(pfs.at(\freq).value, freqSpec.map(0.1), "Restored freq should be mapped to spec");
        this.assertFloatEquals(pfs.at(\amp).getUnmapped, 0.9, "Restored amp should be 0.9");
        this.assertFloatEquals(pfs.at(\amp).value, ampSpec.map(0.9), "Restored amp should be mapped to spec");
    }

    test_remove_and_removeSnapshot {
        var pfs = ParamFuncSet();
        pfs.add(\freq, {|mapped, obj| }, [10, 1000, \exp].asSpec);
        pfs.snapshot(\snap1);
        pfs.remove(\freq);
        this.assert(pfs.at(\freq).isNil, "Removed key should be nil");
        pfs.removeSnapshot(\snap1);
        this.assert(pfs.snapshots[\snap1].isNil, "Removed snapshot should be nil");
    }
}
