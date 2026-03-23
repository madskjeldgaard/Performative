ParamFuncSetGui {
    var <set;  // The ParamFuncSet being controlled
    var <window;
    var paramViews;
    var snapshotPopup, snapshotNameInput;
    var saveSnapshotButton, recallSnapshotButton, deleteSnapshotButton;
    var randomizeAllButton;
    var font, headerFont;

    // Callback functions for updates
    var setChangeFunc;
    var paramChangeFuncs;

    // Fixed sizes for consistent layout
    const label_width = 200;
    const numbox_width = 200;

    *new { |set, parent, bounds, show = true|
        ^super.new().init(set, parent, bounds, show);
    }

    init { |newSet, parent, bounds, show|
        paramViews = IdentityDictionary.new;
        paramChangeFuncs = IdentityDictionary.new;
        this.initFonts();
        set = newSet;
        this.createGui(parent, bounds, show);
        this.setupDependencies();
    }

    initFonts {
        var fontSize, headerFontSize;
        fontSize = 12;
        headerFontSize = fontSize * 1.5;
        headerFont = Font.sansSerif(headerFontSize, bold: true);
        font = Font.monospace(fontSize);
    }

    setupDependencies {
        // Create callback function for set-level changes
        setChangeFunc = {
            { this.updateAllParams() }.defer;
        };

        // Set the callback on the set
        set.changeCallback = setChangeFunc;

        // Create and set callbacks for each ParamFunc
        set.params.keysValuesDo { |key, paramFunc|
            // Make sure it's actually a ParamFunc
            if(paramFunc.isKindOf(ParamFunc)) {
                var paramChangeFunc = {
                    "Updating parameter '%'".format(key).postln;
                    { this.updateParam(key) }.defer;
                };
                "Adding callback for parameter '%'".format(key).postln;
                paramChangeFuncs.put(key, paramChangeFunc);
                paramFunc.changeCallback_(paramChangeFunc);
            } {
                "ParamFuncSetGui: Warning - '%' is not a ParamFunc (got %), GUI may not work correctly".format(key, paramFunc.class).warn;
            };
        };
    }

    removeDependencies {
        // Remove set callback
        if(set.notNil) {
            set.changeCallback = nil;
        };

        // Remove param callbacks
        set.params.keysValuesDo { |key, paramFunc|
            if(paramFunc.isKindOf(ParamFunc)) {
                paramFunc.changeCallback = nil;
            };
        };

        paramChangeFuncs.clear;
    }

    // Update a single parameter in the GUI
    updateParam { |key|
        var paramFunc = set.params[key];
        var val;

        // Skip if not a ParamFunc
        if(paramFunc.isKindOf(ParamFunc).not) {
            ^this;
        };

        val = paramFunc.value;

        defer{
            if(paramViews[key].notNil) {
                case
                { paramViews[key][\type] == \number } {
                    var spec = paramFunc.spec;
                    var numBox = paramViews[key][\numBox];
                    var slider = paramViews[key][\slider];

                    // Update number box
                    numBox.value_(
                        if(spec.respondsTo(\constrain)) {
                            spec.constrain(val)
                        } {
                            val
                        }
                    );

                    // Update slider
                    slider.value_(
                        if(spec.respondsTo(\unmap)) {
                            spec.unmap(val)
                        } {
                            // Fallback linear mapping for numbers
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
                { paramViews[key][\type] == \array } {
                    var spec = paramFunc.spec;
                    var idx = spec.array.indexOf(val) ? 0;
                    paramViews[key][\slider].value_(idx / (spec.array.size - 1).max(0));
                    paramViews[key][\display].string_(val.asString);
                }
                { paramViews[key][\type] == \specList } {
                    // Update each sub-control
                    var subViews = paramViews[key][\subViews];
                    val.do { |subVal, index|
                        var spec = paramFunc.spec[index];
                        if(subViews[index].notNil) {
                            var numBox = subViews[index][\numBox];
                            var slider = subViews[index][\slider];

                            numBox.value_(
                                if(spec.respondsTo(\constrain)) {
                                    spec.constrain(subVal)
                                } {
                                    subVal
                                }
                            );

                            slider.value_(
                                if(spec.respondsTo(\unmap)) {
                                    spec.unmap(subVal)
                                } {
                                    var minval = spec.respondsTo(\minval) !? { spec.minval } ?? 0;
                                    var maxval = spec.respondsTo(\maxval) !? { spec.maxval } ?? 1;
                                    (subVal - minval) / (maxval - minval)
                                }
                            );
                        };
                    };
                };
            }
        }
    }

    // Update all GUI widgets from current parameter values
    updateAllParams {
        set.params.keysValuesDo { |key, paramFunc|
            this.updateParam(key);
        };

        // Also update snapshot popup in case snapshots changed
        this.updateSnapshotPopup();
    }

    updateSnapshotPopup {
        var names = set.getSnapshotNames();
        if(snapshotPopup.notNil) {
            snapshotPopup.items = if(names.isEmpty) { #["No Snapshots"] } { names };
            snapshotPopup.value = 0;
        };
    }

    rebuildView {
        if(window.notNil and: { window.isClosed.not }) {
            window.view.removeAll;
            window.layout = this.makeViewLayout();
            window.view.children.do{ |c| c.font = font };
        };
    }

    createGui { |parent, bounds, show|
        if(parent.isNil) {
            // Create standalone window
            window = Window.new("ParamFuncSet: %".format(set.identityHash),
                bounds ? Rect(0, 0, Window.screenBounds.width * 0.75, Window.screenBounds.height * 0.75)
            );
            window.layout = this.makeViewLayout();
            window.view.children.do{ |c| c.font = font };

            if(show) {
                window.front;
            };

            window.onClose = {
                this.removeDependencies();
                paramViews.clear;
            };
        } {
            // Create view within parent
            var mainView;

            mainView = if(bounds.isNil) {
                View(parent, Rect(0, 0, Window.screenBounds.width * 0.75, Window.screenBounds.height * 0.75)).layout_(VLayout.new());
            } {
                View(parent, bounds).layout_(VLayout.new());
            };

            mainView.layout.add(this.makeViewLayout());
            mainView.children.do{ |c| c.font = font };

            // Store the view in window variable for consistency
            window = mainView;
        };
    }

    makeViewLayout {
        var mainLayout = VLayout.new();

        // Header with title
        mainLayout.add(
            StaticText.new().string_("ParamFuncSet Controls").font_(headerFont)
        );

        // Control section (snapshots, randomize)
        mainLayout.add(this.makeControlSection());

        // Parameters section (scrollable if too many)
        mainLayout.add(this.makeParametersSection(), 1);

        ^mainLayout;
    }

    makeControlSection {
        var controlLayout = VLayout.new();
        var snapshotLayout, buttonLayout, randomizeLayout;

        // Snapshot name input - with fixed width for consistency
        snapshotNameInput = TextField.new()
        .string_(Date.localtime.stamp) ;

        // Snapshot buttons - all same size
        saveSnapshotButton = Button.new()
        .states_([["Save"]])
        .action_({
            var name = snapshotNameInput.string;
            if(name.notEmpty) {
                set.snapshot(name.asSymbol);
                snapshotNameInput.string = "snapshot_" ++ Date.localtime.stamp;
            };
        })
        .toolTip_("Save current state as snapshot");

        recallSnapshotButton = Button.new()
        .states_([["Recall"]])
        .action_({
            if(snapshotPopup.items[0] != "No Snapshots") {
                set.restoreSnapshot(snapshotPopup.items[snapshotPopup.value]);
            };
        })
        .toolTip_("Restore selected snapshot");

        deleteSnapshotButton = Button.new()
        .states_([["Delete"]])
        .action_({
            if(snapshotPopup.items[0] != "No Snapshots") {
                var name = snapshotPopup.items[snapshotPopup.value];
                set.removeSnapshot(name);
            };
        })
        .toolTip_("Delete selected snapshot");

        // Snapshot popup
        snapshotPopup = PopUpMenu.new()
        .items_(set.getSnapshotNames)
        .action_({ |pop|
            // Just selection - recall happens via button
        });

        if(snapshotPopup.items.isEmpty) {
            snapshotPopup.items = #["No Snapshots"];
        };

        // Randomize button
        randomizeAllButton = Button.new()
        .states_([["Randomize All"]])
        .action_({
            set.randomizeAll();
        })
        .toolTip_("Randomize all parameters");

        // Layouts with consistent spacing
        snapshotLayout = HLayout.new(
            [StaticText.new().string_("Snapshot name:"), s: 1],
            snapshotNameInput
        );

        buttonLayout = HLayout.new(
            saveSnapshotButton,
            recallSnapshotButton,
            deleteSnapshotButton
        );

        randomizeLayout = HLayout.new(
            [StaticText.new().string_("Snapshots:"), s: 1],
            snapshotPopup,
            randomizeAllButton
        );

        controlLayout.add(snapshotLayout);
        controlLayout.add(buttonLayout);
        controlLayout.add(randomizeLayout);

        ^controlLayout;
    }

    makeParametersSection {
        var paramView = View.new().layout_(VLayout.new());

        paramViews.clear;

        // Sort keys for consistent display
        set.params.keys.asArray.sort.do { |key|
            var paramFunc = set[key.postln];

            // Skip if not a ParamFunc
            if(paramFunc.isKindOf(ParamFunc)) {
                var paramLayout = this.makeParameterView(key, paramFunc);
                paramView.layout.add(paramLayout);
            } {
                // Display a warning in the GUI for invalid entries
                var errorLayout = HLayout.new(
                    StaticText.new().string_(key.asString).fixedWidth_(label_width),
                    StaticText.new().string_("ERROR: Not a ParamFunc").stringColor_(Color.red)
                );
                paramView.layout.add(errorLayout);
            };
        };

        // Make scrollable if too many parameters
        if(set.params.size > 8) {
            ^ScrollView.new().canvas_(paramView);
        } {
            ^paramView;
        };
    }

    makeParameterView { |key, paramFunc|
        var layout = HLayout.new();

        var paramVal = paramFunc.value;
        var spec = paramFunc.spec;
        var controlMode = paramFunc.controlMode;

        // Fixed width label
        layout.add(
            StaticText.new().string_(key.asString).fixedWidth_(label_width)
        );

        case
        // For ArrayedSpec (discrete choices)
        { controlMode == \arrayspec } {
            var arrayChoices = spec.array;
            var currentIndex = arrayChoices.indexOf(paramVal) ? 0;
            var numChoices = arrayChoices.size;
            var valueText = arrayChoices[currentIndex].asString;
            var valueDisplay, slider;

            valueDisplay = TextField.new()
            .string_(valueText)
            .fixedWidth_(numbox_width);

            slider = Slider.new()
            .orientation_(\horizontal)
            .value_(currentIndex / (numChoices - 1).max(0))
            .step_(1/(numChoices-1))
            .action_({ |sl|
                var index = (sl.value * (numChoices - 1)).round(1).asInteger;
                var val = arrayChoices[index];
                valueDisplay.string_(val.asString);
                paramFunc.setRaw(val);
            });

            paramViews.put(key, (type: \array, slider: slider, display: valueDisplay));

            layout.add(valueDisplay);
            layout.add(slider, 4);
        }

        // For specList (array of ControlSpecs)
        { controlMode == \specList } {
            var subLayout = VLayout.new();
            var subViews = List.new();

            spec.do { |specItem, index|
                var itemLayout = HLayout.new();
                var itemVal = paramVal[index];
                var itemSlider, itemBox;
                var minval, maxval;

                // Indented label for sub-parameter
                itemLayout.add(
                    StaticText.new().string_("  %[%]".format(key, index)).fixedWidth_(label_width - 10),
                    1
                );

                // Get min and max from the spec
                minval = if(specItem.respondsTo(\minval)) { specItem.minval } { 0 };
                maxval = if(specItem.respondsTo(\maxval)) { specItem.maxval } { 1 };

                itemBox = NumberBox.new()
                .action_({ |obj|
                    var val = if(specItem.respondsTo(\constrain)) {
                        specItem.constrain(obj.value)
                    } {
                        obj.value.clip(minval, maxval)
                    };
                    var currentVals = paramFunc.value.copy;
                    currentVals[index] = val;
                    paramFunc.setRaw(currentVals);
                    itemSlider.value_(
                        if(specItem.respondsTo(\unmap)) {
                            specItem.unmap(val)
                        } {
                            (val - minval) / (maxval - minval)
                        }
                    );
                })
                .decimals_(3)
                .value_(
                    if(specItem.respondsTo(\constrain)) {
                        specItem.constrain(itemVal)
                    } {
                        itemVal.clip(minval, maxval)
                    }
                )
                .fixedWidth_(numbox_width);

                itemSlider = Slider.new()
                .orientation_(\horizontal)
                .value_(
                    if(specItem.respondsTo(\unmap)) {
                        specItem.unmap(itemVal)
                    } {
                        (itemVal - minval) / (maxval - minval)
                    }
                )
                .action_({ |sl|
                    var val = if(specItem.respondsTo(\map)) {
                        specItem.map(sl.value)
                    } {
                        minval + (sl.value * (maxval - minval))
                    };
                    var currentVals = paramFunc.value.copy;
                    currentVals[index] = val;
                    itemBox.value = val;
                    paramFunc.setRaw(currentVals);
                });

                itemLayout.add(itemBox);
                itemLayout.add(itemSlider, 4);

                subLayout.add(itemLayout);
                subViews.add((type: \number, slider: itemSlider, numBox: itemBox));
            };

            paramViews.put(key, (type: \specList, subViews: subViews));

            ^subLayout; // Return the sublayout directly, bypassing the outer layout
        }

        // For regular number parameters (ControlSpec, etc.)
        { paramVal.isNumber } {
            var minval, maxval, slider, numBox;

            minval = if(spec.respondsTo(\minval)) { spec.minval } { 0 };
            maxval = if(spec.respondsTo(\maxval)) { spec.maxval } { 1 };

            slider = Slider.new()
            .orientation_(\horizontal)
            .value_(
                if(spec.respondsTo(\unmap)) {
                    spec.unmap(paramVal)
                } {
                    (paramVal - minval) / (maxval - minval)
                }
            )
            .action_({ |obj|
                var val = if(spec.respondsTo(\map)) {
                    spec.map(obj.value)
                } {
                    minval + (obj.value * (maxval - minval))
                };
                numBox.value = val;
                paramFunc.setRaw(val);
            });

            numBox = NumberBox.new()
            .action_({ |obj|
                var val = if(spec.respondsTo(\constrain)) {
                    spec.constrain(obj.value)
                } {
                    obj.value.clip(minval, maxval)
                };
                slider.value_(
                    if(spec.respondsTo(\unmap)) {
                        spec.unmap(val)
                    } {
                        (val - minval) / (maxval - minval)
                    }
                );
                paramFunc.setRaw(val);
            })
            .decimals_(4)
            .value_(
                if(spec.respondsTo(\constrain)) {
                    spec.constrain(paramVal)
                } {
                    paramVal.clip(minval, maxval)
                }
            )
            .fixedWidth_(numbox_width);

            paramViews.put(key, (type: \number, slider: slider, numBox: numBox));

            layout.add(numBox);
            layout.add(slider, 4);
        }

        {
            // Fallback for unsupported types
            layout.add(
                StaticText.new().string_("Unsupported parameter type").stringColor_(Color.red)
            );
        };

        ^layout;
    }


    set_ { |newSet|
        // Remove dependencies from old set
        if(set.isNil.not, {
            this.removeDependencies();
        });

        "Setting new set".postln;
        // Update set
        set = newSet;

        // Clear and rebuild view
        paramViews.clear;
        paramChangeFuncs.clear;
        this.rebuildView();

        // Setup dependencies for new set
        this.setupDependencies();

        // Update values
        this.updateAllParams();
    }

    refresh {
        this.updateAllParams();
    }

    close {
        if(window.notNil) {
            if(window.isKindOf(Window)) {
                window.close;
            } {
                // If it's a View, just remove it from parent
                window.remove;
            };
        };
        this.removeDependencies();
    }
}

+ ParamFunc {
    asView { |parent, bounds, font, key="Parameter"|
        var view, layout;
        var paramVal, valueBox, slider, valueDisplay;
        var minval, maxval;

        // Fixed sizes for consistency
        var labelWidth = 80;
        var numberBoxWidth = 70;

        // Create view with optional parent
        view = if(parent.isNil) {
            View.new().layout_(VLayout.new());
        } {
            View(parent, bounds).layout_(VLayout.new());
        };

        paramVal = this.value;

        layout = HLayout.new(
            [StaticText.new().string_(key).fixedWidth_(labelWidth), s: 1]
        );

        case
        // For ArrayedSpec (discrete choices)
        { spec.class == ArrayedSpec } {
            var arrayChoices = spec.array;
            var currentIndex = arrayChoices.indexOf(paramVal) ? 0;
            var numChoices = arrayChoices.size;
            var valueText = arrayChoices[currentIndex].asString;

            valueDisplay = TextField.new()
            .string_(valueText)
            .fixedWidth_(numberBoxWidth);

            slider = Slider.new()
            .orientation_(\horizontal)
            .value_(currentIndex / (numChoices - 1).max(0))
            .step_(1/(numChoices-1))
            .action_({ |sl|
                var index = (sl.value * (numChoices - 1)).round(1).asInteger;
                var val = arrayChoices[index];
                valueDisplay.string_(val.asString);
                this.setRaw(val);
            });

            layout.add(valueDisplay);
            layout.add(slider, 4);
        }
        // For specList (array of ControlSpecs)
        { controlMode == \specList } {
            // Create a composite view for multi-parameter control
            var multiLayout = VLayout.new();
            var sliders = List.new();
            var boxes = List.new();

            spec.do { |specItem, index|
                var itemLayout = HLayout.new();
                var itemVal = paramVal[index];
                var itemSlider, itemBox;

                // Get min and max from the spec
                minval = if(specItem.respondsTo(\minval)) { specItem.minval } { 0 };
                maxval = if(specItem.respondsTo(\maxval)) { specItem.maxval } { 1 };

                // Indented label for sub-parameter
                itemLayout.add(
                    StaticText.new().string_("  %[%]".format(key, index)).fixedWidth_(labelWidth - 10),
                    1
                );

                itemBox = NumberBox.new()
                .action_({ |obj|
                    var val = if(specItem.respondsTo(\constrain)) {
                        specItem.constrain(obj.value)
                    } {
                        obj.value.clip(minval, maxval)
                    };
                    var currentVals = this.value.copy;
                    currentVals[index] = val;
                    this.setRaw(currentVals);
                    itemSlider.value_(
                        if(specItem.respondsTo(\unmap)) {
                            specItem.unmap(val)
                        } {
                            (val - minval) / (maxval - minval)
                        }
                    );
                })
                .decimals_(3)
                .value_(
                    if(specItem.respondsTo(\constrain)) {
                        specItem.constrain(itemVal)
                    } {
                        itemVal.clip(minval, maxval)
                    }
                )
                .fixedWidth_(numberBoxWidth);

                itemSlider = Slider.new()
                .orientation_(\horizontal)
                .value_(
                    if(specItem.respondsTo(\unmap)) {
                        specItem.unmap(itemVal)
                    } {
                        (itemVal - minval) / (maxval - minval)
                    }
                )
                .action_({ |sl|
                    var val = if(specItem.respondsTo(\map)) {
                        specItem.map(sl.value)
                    } {
                        minval + (sl.value * (maxval - minval))
                    };
                    var currentVals = this.value.copy;
                    currentVals[index] = val;
                    itemBox.value = val;
                    this.setRaw(currentVals);
                });

                itemLayout.add(itemBox);
                itemLayout.add(itemSlider, 4);

                multiLayout.add(itemLayout);
                sliders.add(itemSlider);
                boxes.add(itemBox);
            };

            layout = multiLayout;
        }
        // For regular number parameters (including ControlSpec and regular Spec)
        { paramVal.isNumber } {
            // Get min and max values from the spec
            minval = if(spec.respondsTo(\minval)) { spec.minval } { 0 };
            maxval = if(spec.respondsTo(\maxval)) { spec.maxval } { 1 };

            slider = Slider.new()
            .orientation_(\horizontal)
            .value_(
                if(spec.respondsTo(\unmap)) {
                    spec.unmap(paramVal)
                } {
                    // Fallback linear mapping
                    (paramVal - minval) / (maxval - minval)
                }
            )
            .action_({ |obj|
                var val = if(spec.respondsTo(\map)) {
                    spec.map(obj.value)
                } {
                    // Fallback linear mapping
                    minval + (obj.value * (maxval - minval))
                };
                valueBox.value = val;
                this.setRaw(val);
            });

            valueBox = NumberBox.new()
            .action_({ |obj|
                var val = if(spec.respondsTo(\constrain)) {
                    spec.constrain(obj.value)
                } {
                    obj.value.clip(minval, maxval)
                };
                slider.value_(
                    if(spec.respondsTo(\unmap)) {
                        spec.unmap(val)
                    } {
                        // Fallback linear mapping
                        (val - minval) / (maxval - minval)
                    }
                );
                this.setRaw(val);
            })
            .decimals_(3)
            .value_(
                if(spec.respondsTo(\constrain)) {
                    spec.constrain(paramVal)
                } {
                    paramVal.clip(minval, maxval)
                }
            )
            .fixedWidth_(numberBoxWidth);

            layout.add(valueBox);
            layout.add(slider, 4);
        }
        {
            "'%' parameter ignored".format(this.class).warn;
        };

        // Add the layout to the view
        if(controlMode == \specList) {
            view.layout.add(layout);
        } {
            view.layout.add(layout);
        };

        // Apply font if provided
        if(font.notNil) {
            view.children.do{ |c| c.font = font };
        };

        ^view
    }

    gui{|key="parameter"|
        var window = Window.new("ParamFunc GUI - %".format(key));
        window.layout = VLayout.new();
        window.layout.add(this.asView(window, key: key));
        window.front();
    }
}

+ ParamFuncSet{
    asView { |parent, bounds, font|
        var view = if(parent.isNil) {
            View.new().layout_(VLayout.new());
        } {
            View(parent, bounds).layout_(VLayout.new());
        };

        var setGui = ParamFuncSetGui(this);
        view.layout.add(setGui.window);

        ^view;
    }

    gui { |key="ParamFuncSet"|
        var window = Window.new("ParamFuncSet GUI - %".format(key));
        window.layout = VLayout.new();
        window.layout.add(this.asView(window));
        window.front();
    }
}
