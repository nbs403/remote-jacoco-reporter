# Remote Jacoco Coverage Reporting
Jacoco is a great tool for generating code coverage of unit tests and is capable of generating code coverage from remote
instrumented runnin JVM apps.


This project is a Junit5 extension of Jacoco reporting feature that can generate test scenarios code coverage from remote
instrumented running JVM apps. Code coverage tools, out o being a true measure of code quality; coverage
report is an accumulative view of all paths hit by tests, it is not a guarantee that all individual scenarios are covered. More on this later.

Another feature offered by this project is the ability to setup code coverage reporting and merging from within the test
code itself avoiding different required setup for each build tool, e.g. Gradle, Maven,..., etc. (More enhancements in the pipeline of future releases) 


# Design and Implementation Notes

 ## What is a scenario?
 Basically, speaking of test automation implementation, a scenario is implemented in a single test method, in multiple test methods or test classes or a combination of all.
 
 A scenario or a step of that scenario is identified in test code by annotating test methods and/or classes with
  JacocoReport(scenario=”....”) extension.
 
 Before we dig deeper, let’s highlight some details:
 1.	If a test class is tagged with JacocoReport, then all tests in the class are part of that scenario
 2.	If a test method or its class is tagged with JaccoReport but no scenario name is set, then a new scenario is reported 
 with its name as the test method name
 3.	A scenario can span multiple classes and multiple tests in different classes
 
 ## Behind the scenes
 1.	Before test execution (after @BeforeAll or @BeforeEach), a request is made to Jacoco server to reset its execution data
 2.	After test execution (before @AfterAll or @AfterEach) a request is made to Jacoco server to dump its collected execution data
 3.	An HTML report is generated for that test coverage
 4.	If this test is tagged with a scenario - scenario name is not blank- then this test coverage data is merged with all previous tests coverage collected that belong to the same scenario
 
 One of the best ways of illustrating the features and howto's could be an example, but let's first provide a little more
  of details
  
 Example of scenarios coverage
 Let’s consider the test classes and methods below
 
 ```
 @JacocoReport(scenario = "Acceptance Tests")
 public class TestClassA {
 
 @JacocoReport(scenario = "GDPR")
 public class PrivacyTests {
 
 @JacocoReport(scenario = "GDPR")
 public class OptInOptOutTests {
   
 
 @JacocoReport(scenario = "Lost and found")
 public class FindRecordsTests {
    
     @Test
     @JacocoReport(scenario = "Lost and found")
     public void FindRecordById() {
       
     @Test
     @JacocoReport(scenario = "Negative Tests")
     public void FindNonExistingRecordFail() {
 ```     
This will generate the following reports
```
├── coveragereport
│   ├── scenarios
│   │   ├── Acceptance Tests
│   │   ├── GDPR
│   │   ├── Lost and found
│   │   └── Negative Tests
│   └── tests
│       └── ……

 
``` 
# Next Steps
This is still in early development and there are many areas where this extension can benefit from.
Here is a preliminary list in no specific order
- Support other test frameworks, e.g. TestNG
- Support multiple remote Jacoco servers for different instrumented apps
- Enhancements to retrieving source code and compiled classes of apps under tests
- More testing. Resolve TODO's in this project tests
