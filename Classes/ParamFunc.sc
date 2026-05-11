// ParamFunc and ParamFuncSet are classes for managing parameters with associated callback functions and specs. They allow you to define parameters that automatically call a function when their value changes, and to manage collections of such parameters with the ability to save and restore snapshots of their states.
ParamFunc {
    var <func, <spec, <source, <normalizedValue, <oscFunc;
    var <isListOfSpecs = false;
    var <locked = false;
    var <controlMode;
    var <lastRawValue;
    var <changeCallback; // Callback when it is changed
    var <oscPath;
    var <oscFromNetAddr;
    var <oscResponseNetAddr;

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
            { isArray || isArraySpec } { \arrayspec }
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
            if(value.notNil, {
                source = value;

                if(value != lastRawValue) {
                    func.value(value, this);
                    lastRawValue = value;
                };

                this.changed();

                this.sendOSC();

            }, {
                "ParamFunc: Can't set value to nil".error;
            })
        })
    }

    map { |value|
        if(value.notNil, {

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
                            var mapped = spec.collect{|specItem, index| specItem.map(value[index])};
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
                this.sendOSC();
            })
        }, {
                "ParamFunc: Can't map value to nil".error;
        })
    }

    // Manually trigger callback with latest values
    // update{ }

    value {
        ^source;
    }

    getUnmapped {
        ^this.unmap(source);
    }

    unmap{|value|
        ^if(value.notNil && spec.notNil, {
            spec.unmap(value);
        }, {
            "ParamFunc: Can't unmap value to nil or no spec found".error;
            nil
        })
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
            this.sendOSC();
        })
    }

    // OSC functionality
    /*

    (
        var fromAddr = NetAddr.new("10.109.121.143", 1012);
        var toAddr = fromAddr;
        // Create a frequency parameter with a callback
        f = ParamFunc({ |mapped, obj|
            ("Frequency changed to: " ++ mapped ++ " Hz").postln;
        }, \freq.asSpec);  // Exponential frequency spec (20-20000 Hz)

        f.withOSC("/arm/fader1", fromAddr, toAddr);
    )

    f.map(0.25)

    */
    withOSC {|path, fromNetAddr, responseNetAddr|
        // Store OSC addresses
        oscPath = path;
        oscFromNetAddr = fromNetAddr;
        oscResponseNetAddr = responseNetAddr;

        // Free existing OSCFunc if any
        if(oscFunc.notNil, {
            oscFunc.free();
            oscFunc = nil;
        });

        // Create new OSCFunc to receive normalized values
        oscFunc = OSCFunc({ |msg, time, addr, recvPort|
            var normVal;

            // Extract normalized value from OSC message
            // Supports both single values and arrays
            if(msg.size > 1) {
                if(msg.size == 2) {
                    // Single value
                    normVal = msg[1];
                } {
                    // Array of values
                    normVal = msg[1..];
                };

                // Map the normalized value through the spec
                this.map(normVal);
            } {
                "ParamFunc OSC: Received message without value on path %".format(oscPath).warn;
            }
        }, oscPath, fromNetAddr);

        "ParamFunc: OSC listener added on path %".format(oscPath).postln;
    }

    sendOSC {
        var normVal;

        // Only send OSC if we have a response address
        if(oscResponseNetAddr.notNil) {
            // Get normalized value
            normVal = this.getUnmapped;

            if(normVal.notNil) {
                // Send normalized value back
                oscResponseNetAddr.sendMsg(oscPath, normVal);
            };
        };
    }

    // Clean up OSC resources
    freeOSC {
        if(oscFunc.notNil) {
            oscFunc.free();
            oscFunc = nil;
        };
        oscPath = nil;
        oscFromNetAddr = nil;
        oscResponseNetAddr = nil;
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

    // New OSC tests
    test_oscBasicSetup {
        var pf = ParamFunc({|mapped, obj| }, [0, 10].asSpec);
        var testAddr = NetAddr("127.0.0.1", 57120);
        var responseAddr = NetAddr("127.0.0.1", 57121);

        pf.withOSC("/test/param", testAddr, responseAddr);

        this.assert(pf.oscFunc.notNil, "OSCFunc should be created");
        this.assertEquals(pf.oscPath, "/test/param", "OSC path should be set");
        this.assertEquals(pf.oscResponseNetAddr, responseAddr, "Response address should be set");

        // Cleanup
        pf.freeOSC();
    }

    test_oscReceivingValues {
        var pf = ParamFunc({|mapped, obj| }, [0, 100].asSpec);
        var testAddr = NetAddr("127.0.0.1", 57120);
        var responseAddr = NetAddr("127.0.0.1", 57121);
        var receivedValue = false;

        // Set up change callback to verify value was received
        pf.changeCallback_({|paramFunc|
            receivedValue = true;
        });

        pf.withOSC("/test/param", testAddr, responseAddr);

        // Simulate receiving an OSC message
        testAddr.sendMsg("/test/param", 0.75);

        // Wait for async OSC processing
        0.1.wait;

        this.assert(receivedValue, "Callback should be triggered by OSC message");
        this.assertEquals(pf.value, 75, "Value should be mapped from normalized 0.75 to 75");
        this.assertFloatEquals(pf.getUnmapped, 0.75, "Unmapped value should be 0.75");

        // Cleanup
        pf.freeOSC();
    }

    test_oscResponseSending {
        var pf = ParamFunc({|mapped, obj| }, [0, 1].asSpec);
        var testAddr = NetAddr("127.0.0.1", 57120);
        var responseAddr = NetAddr("127.0.0.1", 57121);
        var oscReceived = false;
        var receivedValue;

        // Set up a listener on the response address
        var responseListener = OSCFunc({ |msg|
            oscReceived = true;
            receivedValue = msg[1];
        }, "/test/response", responseAddr);

        pf.withOSC("/test/response", testAddr, responseAddr);

        // Change the value through map
        pf.map(0.75);

        0.1.wait;

        // Test if OSC message was sent to response address
        this.assert(oscReceived, "OSC response should be sent when value changes");
        this.assertFloatEquals(receivedValue, 0.75, "Sent OSC value should be 0.75");

        // Cleanup
        pf.freeOSC();
        responseListener.free();
    }

    test_oscSendOnAllChanges {
        var pf = ParamFunc({|mapped, obj| }, [20, 20000, \exp].asSpec);
        var testAddr = NetAddr("127.0.0.1", 57120);
        var responseAddr = NetAddr("127.0.0.1", 57121);
        var oscCount = 0;

        var responseListener = OSCFunc({ |msg|
            oscCount = oscCount + 1;
        }, "/test/freq", responseAddr);

        pf.withOSC("/test/freq", testAddr, responseAddr);

        // Test that OSC is sent on set
        pf.set(440);
        0.1.wait;
        this.assertEquals(oscCount, 1, "OSC should be sent on set");

        // Test that OSC is sent on map
        pf.map(0.5);
        0.1.wait;
        this.assertEquals(oscCount, 2, "OSC should be sent on map");

        // Test that OSC is sent on randomize
        pf.randomize;
        0.1.wait;
        this.assertEquals(oscCount, 3, "OSC should be sent on randomize");

        // Test that OSC is NOT sent when locked
        pf.lock(true);
        pf.map(0.8);
        0.1.wait;
        this.assertEquals(oscCount, 3, "OSC should not be sent when locked");

        // Cleanup
        pf.freeOSC();
        responseListener.free();
    }

    test_oscArrayValues {
        var pf = ParamFunc({|mapped, obj| }, [\freq.asSpec, \amp.asSpec]);
        var testAddr = NetAddr("127.0.0.1", 57120);
        var responseAddr = NetAddr("127.0.0.1", 57121);
        var receivedNormValues;

        var responseListener = OSCFunc({ |msg|
            receivedNormValues = msg[1..];
        }, "/test/arrayparam", responseAddr);

        pf.withOSC("/test/arrayparam", testAddr, responseAddr);

        // Send array of normalized values
        testAddr.sendMsg("/test/arrayparam", 0.3, 0.8);

        0.1.wait;

        this.assertEquals(receivedNormValues, [0.3, 0.8], "OSC should handle array values");
        this.assertFloatEquals(pf.value[0], \freq.asSpec.map(0.3), "First value should be mapped correctly");
        this.assertFloatEquals(pf.value[1], \amp.asSpec.map(0.8), "Second value should be mapped correctly");

        // Cleanup
        pf.freeOSC();
        responseListener.free();
    }

    test_oscFreeAndRecreate {
        var pf = ParamFunc({|mapped, obj| }, [0, 1].asSpec);
        var testAddr1 = NetAddr("127.0.0.1", 57120);
        var testAddr2 = NetAddr("127.0.0.1", 57121);
        var responseAddr = NetAddr("127.0.0.1", 57122);

        pf.withOSC("/test/param1", testAddr1, responseAddr);
        this.assertEquals(pf.oscPath, "/test/param1", "First OSC path should be set");

        // Recreate with different path
        pf.withOSC("/test/param2", testAddr2, responseAddr);
        this.assertEquals(pf.oscPath, "/test/param2", "Second OSC path should be set");
        this.assertEquals(pf.oscFromNetAddr, testAddr2, "Second from address should be set");

        // Cleanup
        pf.freeOSC();
    }
}
