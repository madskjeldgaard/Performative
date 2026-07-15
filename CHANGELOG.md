## [unreleased]

### 🚀 Features

- Snapshot interpolation using ParamFunc's .interpolateSnapshots
- Add more helper functions to ParamFuncSet to make hardware easier
- Support multi param setting

### 🐛 Bug Fixes

- [**breaking**] Some arrayspecs cause issues, caused by normalizedValue. More robust error checking.
- Set new parameter as soon as it is added
- Add title to gui
- Pcontrolgui broken after refactor
- Update lock-button on init
- Use internal quant if no quant supplied in .play()
- Clean up unnecessary warnings

### 💼 Other

- Improvements

### 🚜 Refactor

- [**breaking**] ParamFunc callback gets mapped value and object passed as secondary argument.
- [**breaking**] Remove .setRaw, make all parameters respond equally to .set and .map
- [**breaking**] Remove osc-functionality from ParamFunc

### 🧪 Testing

- Add more testing
## [0.1.0] - 2026-03-24

### 🚀 Features

- Add ParamsDef
- Add special spec stuff
- [**breaking**] Only call paramfunc callback if mapped value changes
- Add lock to ParamFunc
- Add randomize and lock buttons to parameter gui

### 🐛 Bug Fixes

- Pctrldef copy not complete
- [**breaking**] Force all pcontrol params to be indexed using symbol
- [**breaking**] Make ParmaFuncSet presets work
- [**breaking**] No longer return added param after adding
- ParamFunc only call change func when value is different
- Update arrayed spec valuebox properly in gui
- Make paramfuncset gui respond to changes in underlying data
- Symbols not accepted as specs
- Use paramfunc's gui for pctrldef

### 💼 Other

- Save/load presets in paramfuncset

### 🚜 Refactor

- Restructure Pparam to use ParamFunc internally
- Use paramfunc's own view in larger views

### 🧪 Testing

- More Pcontrol tests
- Comment out dubious test
