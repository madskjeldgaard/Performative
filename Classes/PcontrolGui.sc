/*

Make a gui for a Pctrldef or Pcontrol


EXAMPLE:
(

// Create a Pcontrol
p = Pcontrol.new({ |pc|
    pc.addParam(\degree, 0, [-10,10, \lin, 1].asSpec);
    pc.addParam(\amp, 0.1, [0, 1, \lin].asSpec);
    pc.addParam(\scale, Scale.major, [Scale.minor, Scale.major, Scale.melodicMinor]);

    Pbind(
        \scale, pc[\scale].trace,
        \degree,  pc[\degree].trace,
        \amp, pc.at(\amp).trace,
        \dur, 0.25,
    )
});

// Create and show the GUI
g = p.gui;

)
*/

PcontrolGui {
    classvar <>defaultExcludeParams = #[];
    classvar <>defaultIgnoreParams = #[];

    var <pcontrol;  // instance variable with lowercase p
    var collapseArrays;
    var paramViews;  // Now stores the control dictionaries from each param view

    var prExcludeParams;
    var <>ignoreParams;
    var <window;

    var play, quantBox, quantLabel;
    var header, parameterSection;
    var updateInfoFunc;

    var font, headerFont;

    var pcontrolChangedFunc, specChangedFunc;

    var presetPopup, savePresetButton, recallPresetButton, deletePresetButton;
    var interpolateButton, interpolationTimeBox;
    var loadPresetsButton, savePresetsButton;
    var savePresetNameInput;
    var limitScheduler, specAddedFunc;

    *new { | pcontrol, limitUpdateRate = 0, show = true, collapseArrays = false |
        ^super.newCopyArgs(pcontrol, collapseArrays).init(limitUpdateRate, show)
    }

    init { | limitUpdateRate, show |
        this.initFonts();

        paramViews = IdentityDictionary.new();

        window = Window.new(pcontrol.class.name);
        window.layout = VLayout.new(
            this.makeInfoSection(),
            this.makeTransportSection(),
            // parameterSection gets added here in makeParameterSection
        );

        window.view.children.do{ | c | c.font = if(c == header, headerFont, font) };

        this.setUpDependencies(limitUpdateRate.max(0));

        this.makeParameterSection();

        // --- Add GUI as dependant to pcontrol for live updates ---
        if(pcontrol.respondsTo(\addDependant)) {
            pcontrol.addDependant(this);
        };

        if(show) {
            window.front;
        };

        window.onClose = {
            limitScheduler.stop;
            if(pcontrol.respondsTo(\removeDependant)) {
                pcontrol.removeDependant(this);
            };

            Spec.removeDependant(specAddedFunc);
            // Clean up any remaining dependencies
        };
    }

    // Respond to dependency updates from pcontrol
    update { |obj ...args|
        // args[0] could be a symbol like \set, \source, etc.
        // Just refresh all parameter widgets from pcontrol state
        this.updateAllParamsFromPcontrol;
    }

    // update all GUI widgets from pcontrol state using stored control references
    updateAllParamsFromPcontrol {
        pcontrol.params.keysValuesDo { |key, par|
            var param = par.param;
            var val = param.source;
            var controls = paramViews[key];

            if(controls.notNil) {
                case
                { controls[\type] == \number } {
                    var spec = param.spec;
                    controls[\numBox].value_(
                        if(spec.respondsTo(\constrain)) {
                            spec.constrain(val)
                        } {
                            val
                        }
                    );
                    controls[\slider].value_(
                        if(spec.respondsTo(\unmap)) {
                            spec.unmap(val)
                        } {
                            if(val.isNumber) {
                                var minval = spec.respondsTo(\minval) !? { spec.minval } ?? 0;
                                var maxval = spec.respondsTo(\maxval) !? { spec.maxval } ?? 1;
                                (val - minval) / (maxval - minval)
                            } {
                                0
                            }
                        }
                    );
                }
                { controls[\type] == \array } {
                    var spec = param.spec;
                    var idx = spec.array.indexOf(val) ? 0;
                    var numChoices = spec.array.size;
                    controls[\slider].value_(idx / (numChoices - 1).max(0));
                    if(controls[\display].notNil) {
                        controls[\display].string_(val.asString);
                    };
                }
                { controls[\type] == \specList } {
                    var valArray = val;
                    var subViews = controls[\subViews];
                    var spec = param.spec;
                    valArray.do { |subVal, index|
                        var specItem = spec[index];
                        if(subViews[index].notNil) {
                            subViews[index][\numBox].value_(
                                if(specItem.respondsTo(\constrain)) {
                                    specItem.constrain(subVal)
                                } {
                                    subVal
                                }
                            );
                            subViews[index][\slider].value_(
                                if(specItem.respondsTo(\unmap)) {
                                    specItem.unmap(subVal)
                                } {
                                    var minval = specItem.respondsTo(\minval) !? { specItem.minval } ?? 0;
                                    var maxval = specItem.respondsTo(\maxval) !? { specItem.maxval } ?? 1;
                                    (subVal - minval) / (maxval - minval)
                                }
                            );
                        };
                    };
                };
            }
        };

        // Also update quantBox and play button
        if(quantBox.notNil) {
            quantBox.value_(pcontrol.patternProxy.quant ? 0);
        };
        if(play.notNil) {
            play.value_(pcontrol.isPlaying.binaryValue);
        };
    }

    asView { ^window.asView }

    setUpDependencies { | limitUpdateRate |
        var limitOrder, limitDict;

        specAddedFunc = { | obj ...args |
            var key, spec;
            if(args[0] == \add, {
                key = args[1][0];
                spec = args[1][1];
                { this.makeParameterSection() }.defer;
            })
        };

        Spec.addDependant(specAddedFunc);

        pcontrolChangedFunc = if(limitUpdateRate > 0, {
            limitOrder = OrderedIdentitySet.new(8);
            limitDict = IdentityDictionary.new();
            limitScheduler = SkipJack.new({
                if(limitOrder.size > 0, {
                    limitOrder.do{ | key | this.pcontrolChanged(*limitDict[key]) };
                    limitOrder.clear;
                    limitDict.clear;
                });
            }, limitUpdateRate, clock: AppClock);
            { | obj ...args |
                var key = args[0];
                if(key == \set) {
                    args[1].pairsDo { | paramKey, v |
                        key = (key ++ paramKey).asSymbol;
                        limitOrder.add(key);
                        limitDict.put(key, [\set, [paramKey, v]]);
                    }
                } {
                    limitOrder.add(key);
                    limitDict.put(key, args);
                }
            }

        }, {

            { | obj ...args | { this.pcontrolChanged(*args) }.defer }

        });

        // Add dependant to pcontrol
        if(pcontrol.respondsTo(\addDependant)) {
            pcontrol.addDependant(pcontrolChangedFunc);
        };
    }

    pcontrolChanged { | what, args |
        var key;

        case
        { what == \set } {
            key = args[0];
            args.pairsDo { | paramKey, val |
                if(paramViews[paramKey].notNil, {
                    this.parameterChanged(paramKey, val)
                })
            }
        }
        { what == \play } {
            play.value_(1);
        }
        { what == \source } {
            this.makeParameterSection()
        }
        { what == \stop } {
            play.value_(0)
        }
        { what == \quant } {
            quantBox.value_(args[0]);
        }
    }

    parameterChanged { | key, val |
        var param = pcontrol.params[key].param;
        var controls = paramViews[key];

        if(controls.notNil) {
            case
            { val.isNumber } {
                var spec = param.spec;
                controls[\numBox].value_(spec.constrain(val));
                controls[\slider].value_(spec.unmap(val));
            }
            { param.spec.class == ArrayedSpec } {
                var spec = param.spec;
                var idx = spec.array.indexOf(val) ? 0;
                var numChoices = spec.array.size;
                controls[\slider].value_(idx / (numChoices - 1).max(0));
                if(controls[\display].notNil) {
                    controls[\display].string_(val.asString);
                };
            }
            {
                "% parameter '%' not handled".format(this.class, key).warn;
            }
        }
    }

    makeInfoSection {
        var quantLabel;

        quantLabel = StaticText.new()
        .string_("quant:");

        quantBox = NumberBox.new()
        .clipLo_(0.0)
        .decimals_(2)
        .scroll_step_(0.1) // mouse
        .step_(0.1)        // keys
        .action_({ | obj |
            pcontrol.quant_(obj.value);
        })
        .value_(pcontrol.patternProxy.quant ? 0);

        updateInfoFunc = { | pc |
            quantBox.value = pc.patternProxy.quant ? 0;
        };
        updateInfoFunc.value(pcontrol);

        if(pcontrol.class == Pctrldef, {
            header = StaticText.new().string_(pcontrol.key)
        }, {
            header = StaticText.new().string_(pcontrol.class.name)
        });

        ^VLayout.new(
            header,
            HLayout.new(quantLabel, [quantBox, a: \left]),
        )
    }

    makeTransportSection {
        var clear, popup, randomize;
        var presetSection;

        play = Button.new()
        .states_([
            ["play"],
            ["stop", Color.black, Color.grey(0.5, 0.5)],
        ])
        .action_({ | obj |
            if(obj.value == 1, {
                pcontrol.play
            }, {
                pcontrol.stop
            })
        })
        .value_(pcontrol.isPlaying.binaryValue)
        .toolTip_("Play or stop the pattern");

        clear = Button.new()
        .states_(#[
            ["clear"]
        ])
        .action_({ | obj |
            pcontrol.patternProxy.clear;
        })
        .toolTip_("Clear the pattern");

        randomize = Button.new()
        .states_([
            ["randomize"]
        ])
        .action_({ | obj |
            pcontrol.randomizeAll();
            this.updateAllParamsFromPcontrol();
        })
        .toolTip_("Randomize all parameters");

        popup = PopUpMenu.new()
        .allowsReselection_(true)
        .items_(#[
            "file",
            "export as midi",
            "load preset file",
            "save preset file"
        ])
        .action_({ | obj |
            switch(obj.value,
                0, { },
                1, {
                    // Prompt for file name
                    var bpm, duration;
                    var window = Window("Export MIDI", Rect(100, 100, 300, 150));
                    var bpmBox, durationBox, exportButton;
                    var bpmLabel, durationLabel;

                    bpm = TempoClock.default.tempo * 60;
                    duration = 16;

                    bpmBox = NumberBox.new()
                    .value_(bpm)
                    .clipLo_(1)
                    .decimals_(0)
                    .step_(1)
                    .action_({ | obj |
                        bpm = obj.value;
                    });

                    bpmLabel = StaticText.new()
                    .string_("BPM:");
                    durationBox = NumberBox.new()
                    .value_(duration)
                    .clipLo_(0)
                    .decimals_(2)
                    .step_(0.1)
                    .action_({ | obj |
                        duration = obj.value;
                    });

                    durationLabel = StaticText.new()
                    .string_("Duration (seconds):");

                    exportButton = Button.new()
                    .states_(["Export"])
                    .action_({ | btn |
                        if(btn.value == 0, {
                            if(bpm.notNil and: { duration.notNil }, {
                                Dialog.savePanel(okFunc: { |path|
                                    if(path.notNil, {
                                        window.close;
                                        if(path.endsWith(".mid").toLower.not, {
                                            path = path ++ ".mid";
                                        });
                                        pcontrol.exportAsMidi(path, tempoBPM: bpm.asFloat, dur: duration.asFloat);
                                    }, {
                                        "Export cancelled".postln;
                                    });
                                });
                            }, {
                                "Export cancelled".postln;
                            });
                        })
                    });

                    window.layout = VLayout.new(
                        HLayout.new(bpmLabel, bpmBox),
                        HLayout.new(durationLabel, durationBox),
                        HLayout.new(exportButton)
                    );
                    window.front;
                },
                2, {
                    Dialog.openPanel({ |path|
                        pcontrol.loadPresetsFromFile(path);
                        this.updatePresetPopup();
                        this.updateAllParamsFromPcontrol();
                    });
                },
                3, {
                    Dialog.savePanel({ |path|
                        pcontrol.savePresetsToFile(path);
                        this.updatePresetPopup();
                    });
                }
            )
        })
        .toolTip_("Open file import/export menu");

        // === PRESET SECTION ===
        presetPopup = PopUpMenu.new()
        .items_(pcontrol.getPresetNames ? #["No Presets"])
        .action_({ |pop|
            if(pop.items.size > 0 and: { pop.items[0] != "No Presets" }) {
                var presetName = pop.items[pop.value];
                pcontrol.currentPresetName = presetName;
                pcontrol.recallPreset(presetName);
                this.updateAllParamsFromPcontrol();
            }
        })
        .toolTip_("Select preset");

        savePresetNameInput = TextField.new()
        .string_(["lion", "tiger", "elephant", "giraffe", "rhinoceros", "gorilla", "chimpanzee", "wolf", "bear", "panda", "kangaroo", "koala", "zebra", "hippopotamus", "monkey", "dog", "cat", "cow", "sheep", "horse", "pig", "rabbit", "donkey", "goat", "deer", "camel", "fox", "leopard", "cheetah", "buffalo", "bison", "walrus", "otter", "squirrel", "beaver", "moose", "antelope", "badger", "weasel", "ferret", "mink", "porcupine", "armadillo", "anteater", "wolverine", "elk", "reindeer", "caribou", "lynx", "bobcat", "puma", "cougar", "mountain", "wildcat"].choose ++ rrand(0, 100).asString);

        savePresetButton = Button.new()
        .states_([["Save"]])
        .action_({
            var name = savePresetNameInput.string;
            if(name != "" and: { name.notNil }, {
                pcontrol.savePreset(name.asSymbol);
                savePresetNameInput.string = "preset_%".format(Date.localtime.stamp);
                this.updatePresetPopup();
            })
        })
        .toolTip_("Save current settings as preset");

        recallPresetButton = Button.new()
        .states_([["Recall"]])
        .action_({
            if(presetPopup.items.size > 0 and: { presetPopup.items[0] != "No Presets" }) {
                pcontrol.recallPreset(presetPopup.items[presetPopup.value]);
                this.updateAllParamsFromPcontrol();
            }
        })
        .toolTip_("Recall/load selected preset");

        presetSection = VLayout(
            HLayout(
                [StaticText().string_("Presets:"), s:1],
                savePresetNameInput,
                savePresetButton,
                recallPresetButton
            ),
            HLayout(
                presetPopup
            )
        );

        ^VLayout(
            HLayout(
                play, clear, randomize, popup
            ),
            presetSection
        )
    }

    updatePresetPopup {
        var names = pcontrol.getPresetNames;
        var currentIndex = names.indexOf(pcontrol.currentPresetName) ? 0;

        presetPopup.items = if(names.isEmpty) { #["No Presets"] } { names };
        if(names.size > 0) {
            presetPopup.value = currentIndex;
        };
    }

    makeParameterSection {
        var excluded = defaultExcludeParams ++ prExcludeParams;
        var paramViewContainer;

        // Clear existing controls
        paramViews.clear;

        if(parameterSection.notNil, { parameterSection.remove });

        paramViewContainer = View.new().layout_(VLayout.new());

        // Sort keys for consistent display
        pcontrol.params.keys.asArray.sort.do { |key|
            var param = pcontrol.params[key].param;
            var spec = param.spec;

            // Skip excluded parameters
            if(this.paramPresentInArray(key, excluded).not, {
                var paramView, controls;

                // Create view using param.asView
                paramView = param.asView(
                    parent: paramViewContainer,
                    bounds: nil,
                    font: font,
                    key: key.asString
                );

                // Extract controls from the view
                controls = paramView.getProperty(\paramControls);
                if(controls.notNil) {
                    paramViews.put(key, controls);
                };

                paramViewContainer.layout.add(paramView);
            });
        };

        // Make scrollable if too many parameters
        if(pcontrol.params.size > 8) {
            parameterSection = ScrollView.new().canvas_(paramViewContainer);
        } {
            parameterSection = paramViewContainer;
        };

        parameterSection.resizeToHint;
        if(parameterSection.bounds.height > (Window.availableBounds.height * 0.5), {
            parameterSection = ScrollView.new().canvas_(parameterSection);
        });

        window.layout.add(parameterSection, 1);
        { window.view.resizeToHint }.defer(0.07);
    }

    initFonts {
        var fontSize, headerFontSize;

        fontSize = 14;
        headerFontSize = fontSize * 2;

        headerFont = Font.sansSerif(headerFontSize, bold: true, italic: false);
        font = Font.monospace(fontSize, bold: false, italic: false);
    }

    randomize { | randmin = 0.0, randmax = 1.0 |
        this.filteredParamsDo{ | val, spec |
            spec.map(rrand(randmin, randmax))
        }
    }

    vary { | deviation = 0.1 |
        this.filteredParamsDo{ | val, spec |
            spec.map((spec.unmap(val) + 0.0.gauss(deviation)).clip(0, 1))
        }
    }

    excludeParams { ^prExcludeParams }

    excludeParams_ {| value |
        prExcludeParams = value;
        { this.makeParameterSection() }.defer;
    }

    defaults {
        this.filteredParamsDo{ | val, spec |
            spec.default
        }
    }

    close {
        if(window.notNil) {
            if(window.isKindOf(Window)) {
                window.close;
            } {
                window.remove;
            };
        };
    }

    filteredParamsDo { | func |
        this.filteredParams.keysValuesDo{ | key, spec |
            var val = pcontrol.params[key].param.source;
            val = func.value(val, spec);
            pcontrol.setOne(key, val);
        }
    }

    filteredParams {
        var accepted = IdentityDictionary.new;
        var ignored;

        ignored = defaultIgnoreParams ++ ignoreParams ++ defaultExcludeParams ++ prExcludeParams;

        pcontrol.params.keysValuesDo({ | key, param |
            var spec = param.spec;
            var val = param.source;

            if(this.paramPresentInArray(key, ignored).not, {
                if(val.isNumber, {
                    accepted.put(key, spec)
                })
            })
        });

        ^accepted
    }

    paramPresentInArray { | key, array |
        ^array.any{ | param |
            if(param.isString and: { param.indexOf($*).notNil }, {
                param.replace("*", ".*").addFirst($^).add($$).matchRegexp(key.asString)
            }, {
                param.asSymbol == key
            })
        }
    }
}
