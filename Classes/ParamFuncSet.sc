// A collection of ParamFuncs with the ability to save/restore snapshots
ParamFuncSet[]{
    var <params;
    var <snapshots, <snapshotInterpFrom, <snapshotInterpTo, <snapshotName;
    var changeCallback; // Callback when any ParamFunc in the set changes

    *new {
        ^super.new().init;
    }

    init {
        params = IdentityDictionary.new;
        snapshots = IdentityDictionary.new;
        changeCallback = {};
    }

    applyAll {
        params.keysValuesDo{ |name, paramFunc|
            var currentValue = paramFunc.value;
            paramFunc.set(currentValue);
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

    set{|...keysValues|
        var pairs = keysValues.clump(2);
        pairs.do{|pair|
            var key = pair[0];
            var value = pair[1];
            this.setOne(key, value);
        }
    }

    setOne{|key, value|
        var paramFunc = params[key];
        if(paramFunc.notNil) {
            if(value.notNil, {
                paramFunc.set(value);
            }, {
                "ParamFuncSet: Cannot set nil value for key %".format(key).warn;
            });
        } {
            "ParamFuncSet: No ParamFunc found for key %".format(key).warn;
        }
    }

    mapOne{|key, value|
        var paramFunc = params[key];
        if(paramFunc.notNil) {
            if(value.notNil, {
                paramFunc.map(value);
            }, {
                "ParamFuncSet: Cannot map nil value for key %".format(key).warn;
            });
        } {
            "ParamFuncSet: No ParamFunc found for key %".format(key).warn;
        }
    }

    map{|...keysValues|
        var pairs = keysValues.clump(2);
        pairs.do{|pair|
            var key = pair[0];
            var value = pair[1];
            this.mapOne(key, value);
        }
    }

    add { |key, func, controlspec|
        var newFunc, currentValue;
        if(params[key].notNil) {
            this.remove(key);
        };

        newFunc = ParamFunc.new(func, controlspec)
        .changeCallback_({ this.changed });

        params.put(key, newFunc);

        // C Paramfunc
        newFunc.set(newFunc.value);

        this.changed();
    }

    remove { |key|
        params.removeAt(key);

        this.changed();
    }

    at { |key|
        ^params[key];
    }

    // Randomize  except the keys in except
    randomizeAll {|...except|
        params.keysValuesDo{ |key, paramFunc|
            if(except.notNil and: { except.contains(key) }) {} {
                paramFunc.randomize;
            }
        };

        this.changed();
    }

    getSnapshotNames {
        ^snapshots.keys.asArray;
    }

    setCurrentSnapshotName{|newName|
        snapshotName = newName.asSymbol;
        this.changed();
    }

    getCurrentSnapshotName{
        ^snapshotName;
    }

    snapshot{|name|
        // Each snapshot saves the current state of  ParamFuncs in the set
        var newSnapshot = IdentityDictionary.new;
        name = name ? snapshotName ?? {Date.getDate.stamp};
        params.keysValuesDo{ |key, paramFunc|
            var val = paramFunc.value;
            newSnapshot.put(key, val);
        };

        snapshots.put(name, newSnapshot);
        this.changed();
    }

    removeSnapshot{|name|
        snapshots.removeAt(name);
        this.changed();
    }

    restoreSnapshot{|name|
        var snapshot;
        name = name ? snapshotName;
        snapshot = snapshots[name];
        if(snapshot.isNil) {
            "ParamFuncSet: No snapshot found with name %".format(name).error;
        } {
            snapshot.keysValuesDo{ |key, value|
                var paramFunc = params[key];

                if(paramFunc.notNil) {
                    paramFunc.set(value);
                } {
                    "ParamFuncSet: No ParamFunc found for key % in snapshot %".format(key, name).warn;
                }
            }
        };

        this.changed();
    }

    setSnapshotInterpFrom{|name|
        if(name.notNil && this.snapshots.keys.asArray.contains(name), {
            snapshotInterpFrom = name.asSymbol;
            this.changed();
        },{
            "Could not find snapshot name %".format(name).warn
        })
    }

    setSnapshotInterpTo{|name|
        if(name.notNil&& this.snapshots.keys.asArray.contains(name), {
            snapshotInterpTo = name.asSymbol;
            this.changed();
        }, {
            "Could not find snapshot name %".format(name).warn
        })
    }

    // As opposed to the one below, this uses the built in snapshot names set.
    interpolate{|amount|
        this.interpolateSnapshots(this.snapshotInterpFrom, this.snapshotInterpTo, amount)
    }

    interpolateSnapshots{|name1, name2, alpha|
        var snapshot1 = snapshots[name1];
        var snapshot2 = snapshots[name2];

        if(snapshot1.isNil) {
            "ParamFuncSet: No snapshot found with name %".format(name1).error;
            ^this;
        };

        if(snapshot2.isNil) {
            "ParamFuncSet: No snapshot found with name %".format(name2).error;
            ^this;
        };

        snapshot1.keysValuesDo{ |key, value1|
            var value2 = snapshot2[key];
            if(value2.notNil) {
                var paramFunc = params[key];
                if(paramFunc.notNil) {
                    var v1Unmapped = paramFunc.spec.unmap(value1);
                    var v2Unmapped = paramFunc.spec.unmap(value2);
                    var interpValue = paramFunc.spec.map(v1Unmapped.blend(v2Unmapped, alpha));
                    paramFunc.set(interpValue);
                } {
                    "ParamFuncSet: No ParamFunc found for key % in snapshots".format(key).warn;
                }
            } {
                "ParamFuncSet: Key % not found in both snapshots".format(key).warn;
            }
        };

        this.changed();
    }


    //------------------------------------------------------------------//
    //                             Presets                              //
    //------------------------------------------------------------------//
    // This object contains a presets dictionary with  current presets, these can be saved and loaded to/from a file.


    // Saves parameters, parameter states and snapshots to the presets dictionary under the given name, overwriting existing preset with same name
    savePresetsToFile{|filePath|
        var preset = (
            // : all,
            snapshots: snapshots,
            currentValues: params.collect{ |paramFunc, key|  paramFunc.value() }
        );

        preset.writeArchive(filePath);

    }

    loadPreset{|filePath|
        var preset = Object.readArchive(filePath);
        if(preset.isNil) {
            "ParamFuncSet: Failed to load preset from file %".format(filePath).error;
        } {
            // Clear current state
            snapshots.clear;

            // Load new state
            preset.snapshots.keysValuesDo{ |key, snapshot|
                snapshots.put(key, snapshot);
            };

            preset.currentValues.keysValuesDo{ |key, value|
                var paramFunc = params[key];
                if(paramFunc.notNil) {
                    paramFunc.set(value);
                } {
                    "ParamFuncSet: No ParamFunc found for key % in preset file %".format(key, filePath).warn;
                }
            };

            this.changed();
        }
    }

    lockParams{|...paramsToLock|
        params.keysValuesDo{ |key, paramFunc|
            if(paramsToLock.contains(key)) {
                paramFunc.lock(true);
            }
        };
    }

    unlockParams{|...paramsToUnlock|
        params.keysValuesDo{ |key, paramFunc|
            if(paramsToUnlock.contains(key)) {
                paramFunc.lock(false);
            }
        };
    }
}

ParamsDef : ParamFuncSet {
    var <key;
    classvar <>all;

    *new{ arg key;
        var res = this.at(key);
        if(res.isNil) {
            res = super.new().prAdd(key);
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
}

TestParamFuncSet : PerformativeTest {

    setUp {
        // Called before each test
    }

    tearDown {
        // Called after each test
    }

    test_paramlock{
        var pfs = ParamFuncSet();
        pfs.add(\freq, {|mapped, obj| }, [10, 1000].asSpec);
        pfs.lockParams(\freq);
        pfs.at(\freq).set(10);
        this.assertEquals(pfs.at(\freq).value, 10, "Value should not change when locked");
        pfs.unlockParams(\freq);
        pfs.at(\freq).set(5);
        this.assertEquals(pfs.at(\freq).value, 5, "Value should change when unlocked");
        // Test if randomization respects lock
        pfs.lockParams(\freq);
        pfs.randomizeAll();
        this.assertEquals(pfs.at(\freq).value, 5, "Value should not change when locked during randomize");
    }

    test_add_and_at {
        var pfs = ParamFuncSet();
        pfs.add(\freq, {|mapped, obj| }, [10, 1000, \exp].asSpec);
        this.assert(pfs.at(\freq).notNil, "ParamFuncSet should contain freq key");
    }

    test_randomizeAll {
        var pfs = ParamFuncSet();
        pfs.add(\freq, {|mapped, obj| }, [10, 1000, \exp].asSpec);
        pfs.add(\amp, {|mapped, obj| }, \amp.asSpec);
        pfs[\freq].set(0.5);
        pfs.randomizeAll();
        this.assert(pfs.at(\freq).value >= 10 and: { pfs.at(\freq).value <= 1000 }, "Randomized freq should be in range");
        this.assertFloatEquals(pfs.at(\freq).getUnmapped, pfs.at(\freq).spec.unmap(pfs.at(\freq).value), "Unmapped value should match spec unmap of mapped value");
        this.assert(pfs.at(\amp).value >= 0 and: { pfs.at(\amp).value <= 1 }, "Randomized amp should be in range");
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

    test_snapshot_interpolation {
        var pfs = ParamFuncSet();
        var freqSpec = [10, 1000, \exp].asSpec;
        pfs.add(\freq, {|mapped, obj| }, freqSpec);
        pfs.at(\freq).map(0.0);
        pfs.snapshot(\snap1);
        pfs.at(\freq).map(1.0);
        pfs.snapshot(\snap2);
        pfs.interpolateSnapshots(\snap1, \snap2, 0.5);
        this.assertFloatEquals(pfs.at(\freq).getUnmapped, 0.5, "Interpolated freq should be 0.5");
        this.assertFloatEquals(pfs.at(\freq).value, freqSpec.map(0.5), "Interpolated freq should be mapped to spec");
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

    test_save_presets_to_file {
        var tmpdir = PathName.tmp;
        var filePath = tmpdir +/+ "paramFuncSetTest.scd";
        var pfs = ParamFuncSet();
        pfs.add(\freq, {|mapped, obj| }, [10, 1000 , \exp].asSpec);
        pfs.add(\amp, {|mapped, obj| }, \amp.asSpec);
        pfs.at(\freq).set(0.5);
        pfs.at(\amp).set(0.5);
        pfs.savePresetsToFile(filePath);
        this.assert(File.exists(filePath), "Preset file should exist after saving");

        // Clean up
        File.delete(filePath);
    }

    test_load_presets_from_file {
        var tmpdir = PathName.tmp;
        var filePath = tmpdir +/+ "paramFuncSetTest.scd";
        var pfs = ParamFuncSet();
        var loadedPfs;
        pfs.add(\freq, {|mapped, obj| }, [10, 1000 , \exp].asSpec);
        pfs.add(\amp, {|mapped, obj| }, \amp.asSpec);
        pfs.at(\freq).set(0.5);
        pfs.at(\amp).set(0.5);
        pfs.savePresetsToFile(filePath);

        // PF needs to define the paramfuncs before presets can be loaded
        loadedPfs = ParamFuncSet();
        loadedPfs.add(\freq, {|mapped, obj| }, [10, 1000 , \exp].asSpec);
        loadedPfs.add(\amp, {|mapped, obj| }, \amp.asSpec);
        loadedPfs.loadPreset(filePath);
        this.assert(loadedPfs.at(\freq).notNil, "Loaded ParamFuncSet should contain freq key", onFailure: {
                "Loaded ParamFuncSet : %".format(loadedPfs.params.keys).postln;
        });
        this.assert(loadedPfs.at(\amp).notNil, "Loaded ParamFuncSet should contain amp key");
        this.assertFloatEquals(loadedPfs.at(\freq).value, pfs.at(\freq).value, "Loaded freq value should match saved value");
        this.assertFloatEquals(loadedPfs.at(\amp).value, pfs.at(\amp).value, "Loaded amp value should match saved value");

        // Clean up
        File.delete(filePath);
    }

    test_replace_existing_key {
        var pfs = ParamFuncSet();
        var originalFunc, originalSpec;
        // Test if new func, new spec, etc. are replaced when adding new param
        pfs.add(\freq, {|mapped, obj| }, [10, 1000 , \exp].asSpec);
        originalFunc = pfs.at(\freq).func;
        originalSpec = pfs.at(\freq).spec;
        pfs.add(\freq, {|mapped, obj| 222}, \amp.asSpec);
        this.assert(pfs.at(\freq).notNil, "ParamFuncSet should contain freq key after replacement");
        this.assert(pfs.at(\freq).func != originalFunc, "Func should be replaced when adding existing key");
        this.assert(pfs.at(\freq).spec != originalSpec, "Spec should be replaced when adding existing key");
    }

    test_multi_set{
        // Set multiple params at once
        var pfs = ParamFuncSet();
        pfs.add(\freq, {|mapped, obj| }, [10, 1000, \exp].asSpec);
        pfs.add(\amp, {|mapped, obj| }, \amp.asSpec);
        pfs.set(\freq, 0.5, \amp, 0.8);
        this.assertFloatEquals(pfs.at(\freq).value, 0.5, "Freq should be set to 0.5");
        this.assertFloatEquals(pfs.at(\amp).value, 0.8, "Amp should be set to 0.8");
    }

    test_multi_map{
        // Map multiple params at once
        var pfs = ParamFuncSet();
        pfs.add(\freq, {|mapped, obj| }, [10, 1000, \exp].asSpec);
        pfs.add(\amp, {|mapped, obj| }, \amp.asSpec);
        pfs.map(\freq, 0.5, \amp, 0.8);
        this.assertFloatEquals(pfs.at(\freq).getUnmapped, 0.5, "Freq should be mapped to 0.5");
        this.assertFloatEquals(pfs.at(\amp).getUnmapped, 0.8, "Amp should be mapped to 0.8");
    }

}
