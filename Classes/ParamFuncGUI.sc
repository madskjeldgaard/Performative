+ ParamFunc {
    asView { |parent, bounds, font, key="Parameter"|
        var view, layout;
        var paramVal, valueBox, slider, valueDisplay, randomizeButton, lockButton;
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

        // Add "r" momentary button that calls .randomize on the ParamFunc
        randomizeButton = Button.new()
        .states_([["r"]])
        .action_({
            this.randomize;
            slider.value_(spec.unmap(this.value));
            valueBox.value_(this.value);
        });

        // Lock button to toggle whether the parameter is randomizable
        lockButton = Button.new()
        .states_([
            ["locked", Color.black, Color.gray],
            ["locked", Color.black, Color.yellow]
        ])
        .action_({|obj|
            var val = obj.value;

            if(val == 0) {
                this.lock(false)
            } {
                this.lock(true)
            };
        });

        layout.add(*[randomizeButton,  s: 1]);
        layout.add(*[lockButton, s: 1]);

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
        var window = Window.new("%".format(key));
        window.layout = VLayout.new();
        window.layout.add(this.asView(window, key: key));
        window.front();
    }
}
