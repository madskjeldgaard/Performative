// The same as a pattern proxy (pdefn) but with a spec and mapping capabilities;
/*
(
p = Pparam.new(500, Spec.specs[\freq]);
p.map(0.2);
p.source.postln
)

// Using arrayed spec !
(
Pctrldef(\yo)
.addParam(
    \myArrayParam, \hey, [\hey, \yo, \ho]
);

Pctrldef(\yo)[\myArrayParam].source.postln;
Pctrldef(\yo).map(\myArrayParam, 0.5);
Pctrldef(\yo)[\myArrayParam].source.postln;
)


*/
Pparam : PatternProxy{
    var <spec;

    *new{|source, controlspec|
        ^super.new().source_(source).spec_(controlspec ? [ 0.0,1.0,\lin]).setRaw(controlspec.default)
    }

    copy{
        ^this.class.new(this.source, this.spec).envir_(this.envir.copy).spec_(this.spec)
    }

	// copy {
	// 	^super.copy.copyState(this)
	// }

	copyState { |proxy|
		envir = proxy.envir.copy;
		this.source = proxy.source;
	}

    spec_{|newSpec|
        spec = newSpec.asSpec;
    }

    // Set without spec
    setRaw{|value|
        this.source = value;
    }

    set{|value|
        this.setRaw(value);
    }

    value{
        ^this.source;
    }

    // Uses a spec to map it's values (yes, I know, it overwrites original map)
    map{|value|
        if(spec.notNil, {

            var mapped = spec.map(value);
            var step = spec.step;

            this.setRaw(mapped);

        }, {
            "No spec found for %. Using unipolar".format(this.class.name).warn;
            this.setRaw(\uni.asSpec.map(value));
        })
    }

    getUnmapped{
        ^spec.unmap(this.source);
    }


    randomize{
        if(spec.notNil, {
            this.setRaw(spec.randomValue);
        }, {
            "No spec found for %. Using unipolar".format(this.class.name).warn;
            this.setRaw(\uni.asSpec.randomValue)
        });
    }

}

PparamDef : Pparam{
    var <key;
    classvar <>all;

    *new{ arg key, item, controlspec;
        var res = this.at(key);
        if(res.isNil) {
            res = super.new(item, controlspec).prAdd(key);
        } {
            if(item.notNil) { res.source = item }
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

    copy { |toKey|
        if(toKey.isNil or: { key == toKey }) { Error("can only copy to new key (key is %)".format(toKey)).throw };
        ^this.class.new(toKey).copyState(this)
    }
}
