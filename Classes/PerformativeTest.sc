PerformativeTest : UnitTest {
    *runAll{|reset=true, report=true|
        ^PerformativeTest.allSubclasses.collect{|cls| cls.run(reset, report)};
    }
}
