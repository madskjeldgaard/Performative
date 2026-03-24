ParamFuncSetGui {
    var <set;  // The ParamFuncSet being controlled
    var <window;
    var paramViews;
    var snapshotPopup, snapshotNameInput;
    var saveSnapshotButton, recallSnapshotButton, deleteSnapshotButton;
    var randomizeAllButton;
    var font, headerFont;
    var deferDelta = 0.01;

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
        fontSize = 14;
        headerFontSize = fontSize * 1.5;
        headerFont = Font.sansSerif(headerFontSize, bold: true);
        font = Font.monospace(fontSize);
    }

    setupDependencies {
        // Create callback function for set-level changes
        setChangeFunc = {
            { this.updateAllParams() }.defer(deferDelta);
        };

        // Set the callback on the set
        set.changeCallback = setChangeFunc;

        // Create and set callbacks for each ParamFunc
        set.params.keysValuesDo { |key, paramFunc|
            // Make sure it's actually a ParamFunc
            if(paramFunc.isKindOf(ParamFunc)) {
                var paramChangeFunc = {
                    { this.updateParam(key) }.defer(deferDelta);
                };
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

        {
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
        }.defer(deferDelta);
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
            var paramFunc = set[key];

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
        ^paramFunc.asView(window, key: key);
    }

    set_ { |newSet|
        // Remove dependencies from old set
        if(set.isNil.not, {
            this.removeDependencies();
        });

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
