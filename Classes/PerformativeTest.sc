PerformativeTest : UnitTest {
    *runAll{
        ^PerformativeTest.allSubclasses.collect{|cls| cls.run()};
    }
}
