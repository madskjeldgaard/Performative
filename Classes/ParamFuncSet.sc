// A collection of ParamFuncs with the ability to save/restore snapshots
ParamFuncSet[]{
    var <params;
    var <snapshots;
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

    set{|key, value|
        var paramFunc = params[key];
        if(paramFunc.notNil) {
            paramFunc.set(value);
        } {
            "ParamFuncSet: No ParamFunc found for key %".format(key).warn;
        }
    }

    setRaw{|key, value|
        var paramFunc = params[key];
        if(paramFunc.notNil) {
            paramFunc.setRaw(value);
        } {
            "ParamFuncSet: No ParamFunc found for key %".format(key).warn;
        }
    }

    add { |key, func, controlspec|
        var newFunc, currentValue;
        if(params[key].notNil) {
            // "ParamFuncSet: Key % already exists. Removing.".warn;
            this.remove(key);
        };

        newFunc = ParamFunc.new(func, controlspec)
        .changeCallback_({ this.changed });

        params.put(key, newFunc);

        // C Paramfunc
        newFunc.setRaw(newFunc.value);

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

    snapshot{|name|
        // Each snapshot saves the current state of  ParamFuncs in the set
        var newSnapshot = IdentityDictionary.new;
        params.keysValuesDo{ |key, paramFunc|
            var val = paramFunc.value;
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
                var paramFunc = params[key];

                if(paramFunc.notNil) {
                    paramFunc.setRaw(value);
                } {
                    "ParamFuncSet: No ParamFunc found for key % in snapshot %".format(key, name).warn;
                }
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
                    paramFunc.setRaw(value);
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
        pfs.at(\freq).setRaw(10);
        this.assertEquals(pfs.at(\freq).value, 10, "Value should not change when locked");
        pfs.unlockParams(\freq);
        pfs.at(\freq).setRaw(5);
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
        pfs[\freq].setRaw(0.5);
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

}
