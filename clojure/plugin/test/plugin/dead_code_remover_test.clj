(ns plugin.dead-code-remover-test
  (:require [clojure.test :refer :all]
            [clojure.walk :as w]
            [plugin.compiler.ast-utils :as ast-utils]
            [plugin.compiler.core :as cc])
  (:use [plugin.test-utils]))

(defn compile [src]
  (cc/compile-uzi-string src))

(deftest stopped-task-with-no-refs-should-be-removed
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 13}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [],
                             :name "alive1",
                             :ticking true},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 11}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}},
                                            {:__class__ "UziStopScriptInstruction",
                                             :argument "alive2"}],
                             :locals [],
                             :name "alive2",
                             :ticking true}],
                  :variables #{{:__class__ "UziVariable",
                                :value 0},
                               {:__class__ "UziVariable",
                                :value 13},
                               {:__class__ "UziVariable",
                                :value 11}}}
        actual (compile "
                  task alive1() running { toggle(D13); }
	                task dead() stopped { toggle(D12); }
                  task alive2() { toggle(D11); }")]
    (is (= expected actual))))

(deftest start-task-should-mark-the-script-as-alive
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 13}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}},
                                            {:__class__ "UziStartScriptInstruction",
                                             :argument "dead"}],
                             :locals [],
                             :name "alive",
                             :ticking true},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 12}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [],
                             :name "dead",
                             :ticking false}],
                  :variables #{{:__class__ "UziVariable",
                                :value 0},
                               {:__class__ "UziVariable",
                                :value 13},
                               {:__class__ "UziVariable",
                                :value 12}}}
        actual (compile "
                		task alive() running { toggle(D13); start dead; }
                		task dead() stopped { toggle(D12); }
                		task reallyDead() stopped { toggle(D11); }")]
    (is (= expected actual))))

(deftest stop-task-should-mark-the-script-as-alive
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 13}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}},
                                            {:__class__ "UziStopScriptInstruction",
                                             :argument "dead"}],
                             :locals [],
                             :name "alive",
                             :ticking true},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 12}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [],
                             :name "dead",
                             :ticking false}],
                  :variables #{{:__class__ "UziVariable",
                                :value 0},
                               {:__class__ "UziVariable",
                                :value 13},
                               {:__class__ "UziVariable",
                                :value 12}}}
        actual (compile "
              		task alive() running { toggle(D13); stop dead; }
              		task dead() stopped { toggle(D12); }
              		task reallyDead() stopped { toggle(D11); }")]
    (is (= expected actual))))

(deftest pause-task-should-mark-the-script-as-alive
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 13}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}},
                                            {:__class__ "UziPauseScriptInstruction",
                                             :argument "dead"}],
                             :locals [],
                             :name "alive",
                             :ticking true},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 12}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [],
                             :name "dead",
                             :ticking false}],
                  :variables #{{:__class__ "UziVariable",
                                :value 0},
                               {:__class__ "UziVariable",
                                :value 13},
                               {:__class__ "UziVariable",
                                :value 12}}}
        actual (compile "
              		task alive() running { toggle(D13); pause dead; }
              		task dead() stopped { toggle(D12); }
              		task reallyDead() stopped { toggle(D11); }")]
    (is (= expected actual))))

(deftest resume-task-should-mark-the-script-as-alive
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 13}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}},
                                            {:__class__ "UziResumeScriptInstruction",
                                             :argument "dead"}],
                             :locals [],
                             :name "alive",
                             :ticking true},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 12}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [],
                             :name "dead",
                             :ticking false}],
                  :variables #{{:__class__ "UziVariable",
                                :value 0},
                               {:__class__ "UziVariable",
                                :value 13},
                               {:__class__ "UziVariable",
                                :value 12}}}
        actual (compile "
              		task alive() running { toggle(D13); resume dead; }
              		task dead() stopped { toggle(D12); }
              		task reallyDead() stopped { toggle(D11); }")]
    (is (= expected actual))))

(deftest the-visit-order-should-not-matter
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 12}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [],
                             :name "dead",
                             :ticking false},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 13}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}},
                                            {:__class__ "UziStartScriptInstruction",
                                             :argument "dead"}],
                             :locals [],
                             :name "alive",
                             :ticking true}],
                  :variables #{{:__class__ "UziVariable",
                                :value 0},
                               {:__class__ "UziVariable",
                                :value 13},
                               {:__class__ "UziVariable",
                                :value 12}}}
        actual (compile "
                  task dead() stopped { toggle(D12); }
              		task alive() running { toggle(D13); start dead; }
              		task reallyDead() stopped { toggle(D11); }")]
    (is (= expected actual))))


(deftest circular-refs-should-not-be-a-problem
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 13}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}},
                                            {:__class__ "UziStartScriptInstruction",
                                             :argument "bar"}],
                             :locals [],
                             :name "foo",
                             :ticking true},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziStartScriptInstruction",
                                             :argument "foo"},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 12}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [],
                             :name "bar",
                             :ticking false}],
                  :variables #{{:__class__ "UziVariable",
                                :value 0},
                               {:__class__ "UziVariable",
                                :value 13},
                               {:__class__ "UziVariable",
                                :value 12}}}
        actual (compile "
                  task foo() running { toggle(D13); start bar; }
                  task bar() stopped { start foo; toggle(D12); }")]
    (is (= expected actual))))

(deftest stopped-script-that-starts-itself-should-not-count
  (let [expected {:__class__ "UziProgram", :scripts [], :variables #{}}
        actual (compile "task bar() stopped { start bar; toggle(D12); }")]
    (is (= expected actual))))

(deftest unused-globals-should-be-removed
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 1000},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 13}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [],
                             :name "blink13",
                             :ticking true}],
                  :variables #{{:__class__ "UziVariable",
                                :value 1000},
                               {:__class__ "UziVariable",
                                :value 13}}}
        actual (compile "
                  var a = 0;
                  task blink13() running 1/s { toggle(D13); }")]
    (is (= expected actual))))

(deftest unused-globals-should-be-removed-even-if-they-have-references-from-dead-script
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 1000},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 13}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [],
                             :name "blink13",
                             :ticking true}],
                  :variables #{{:__class__ "UziVariable",
                                :value 1000},
                               {:__class__ "UziVariable",
                                :value 13}}}
        actual (compile "
                  var a = 0;
                  task blink13() running 1/s { toggle(D13); }
                  task test() stopped { a = 100; }")]
    (is (= expected actual))))

(deftest used-globals-should-not-be-removed
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 1000},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :name "b"
                                                        :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [],
                             :name "blink13",
                             :ticking true}],
                  :variables #{{:__class__ "UziVariable",
                                :name "b",
                                :value 1},
                               {:__class__ "UziVariable",
                                :value 1000}}}
        actual (compile "
                var a = 0;
                var b = 1;
                task blink13() running 1/s { toggle(b); }
                task test() stopped { a = b + 1; }")]
    (is (= expected actual))))

(deftest unused-globals-should-be-removed-even-if-they-are-hidden-by-a-local
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 1000},
                             :instructions [{:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :name "a#1",
                                                        :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [{:__class__ "UziVariable",
                                       :name "a#1",
                                       :value 13}],
                             :name "blink13",
                             :ticking true}],
                  :variables #{{:__class__ "UziVariable",
                                :value 1000},
                               {:__class__ "UziVariable",
                                :value 13}}}
        actual (compile "
                  var a = 0;
                  task blink13() running 1/s { var a = D13; toggle(a); }")]
    (is (= expected actual))))


(deftest calling-a-script-should-mark-it-as-alive
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [{:__class__ "UziVariable",
                                          :name "speed#1",
                                          :value 0}],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :name "m.reversePin",
                                                        :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "turnOff"}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :name "m.forwardPin",
                                                        :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "turnOn"}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :name "m.enablePin",
                                                        :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :name "speed#1",
                                                        :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "write"}}],
                             :locals [],
                             :name "m.forward",
                             :ticking false},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 1}},
                                            {:__class__ "UziScriptCallInstruction",
                                             :argument "m.forward"},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "pop"}}],
                             :locals [],
                             :name "loop",
                             :ticking true}],
                  :variables #{{:__class__ "UziVariable",
                                :name "m.enablePin",
                                :value 0},
                               {:__class__ "UziVariable",
                                :name "m.forwardPin",
                                :value 0},
                               {:__class__ "UziVariable",
                                :name "m.reversePin",
                                :value 0},
                               {:__class__ "UziVariable",
                                :value 0},
                               {:__class__ "UziVariable",
                                :value 1}}}
        actual (compile "
                  import m from 'DCMotor.uzi';
                  task loop() running { m.forward(1); }")]
    (is (= expected actual))))


(deftest a-more-complete-example
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 500},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 13}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [],
                             :name "blink13",
                             :ticking true},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 1000},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 12}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "toggle"}}],
                             :locals [],
                             :name "blink12",
                             :ticking true},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable",
                                     :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :name "a",
                                                        :value 0}},
                                            {:__class__ "UziJZInstruction",
                                             :argument 3},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 11}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "turnOn"}},
                                            {:__class__ "UziJMPInstruction",
                                             :argument 2},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable",
                                                        :value 11}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive",
                                                        :name "turnOff"}},
                                            {:__class__ "UziStopScriptInstruction",
                                             :argument "setup"}],
                             :locals [],
                             :name "setup",
                             :ticking true}],
                  :variables #{{:__class__ "UziVariable",
                                :name "a",
                                :value 10},
                               {:__class__ "UziVariable",
                                :value 500},
                               {:__class__ "UziVariable",
                                :value 13},
                               {:__class__ "UziVariable",
                                :value 1000},
                               {:__class__ "UziVariable",
                                :value 12},
                               {:__class__ "UziVariable",
                                :value 0},
                               {:__class__ "UziVariable",
                                :value 11}}}
        actual (compile "
                  \"This is just an example of code that uses all the available syntax
                  in the language.\"
                  \"I wrote it to help me create a syntax highlighter for the \"\"Ace\"\" editor\"

                  import foo from 'DCMotor.uzi';
                  import bar from 'Sonar.uzi' {
                    trigPin = 100;
                    echoPin = 200;
                    start reading;
                    stop reading;
                    resume reading;
                    pause reading;
                  }

                  var a = 10;
                  var b = 0.5;
                  var c;

                  task blink13() running 2/s { toggle(D13); }
                  task blink12() running 1/s { toggle(D12); }

                  task setup() {
                    if a { turnOn(D11); }
                    else { turnOff(D11); }
                  }

                  func fact(n) {
                    if n == 0 { return 1; }
                    return n * fact(n - 1);
                  }

                  proc foo_bar_baz(a, b, c) {
                    var d = a * b + c;
                    repeat d { toggle(A2); }
                    forever {
                      start blink13, blink12;
                      stop blink13;
                      yield;
                      pause blink12, blink13;
                      resume blink12; yield;
                      return;
                    }
                    while 1 && 0 { toggle(D10); delayMs(1000); }
                    until 0 || 0 { toggle(D10); delayMs(1000); }
                    while 1 >= 0; \"Body is optional\"
                    until 0 <= 1; \"Body is optional\"
                    do { toggle(D9); } while 1 > 0;
                    do { toggle(D8); } until 0 < 1;
                    for i = 0 to 10 by 1 {
                      toggle(A0);
                      delayMs(i * 100);
                    }
                    var e = foo.getSpeed();
                    foo.init(fact(1 * -2 + -3.5), a + b/d, 0);
                    bar.init(trig: a, echo: b, maxDist: c);
                  }")]
    (is (= expected actual))))


(deftest a-more-complete-example-2
  (let [expected {:__class__ "UziProgram",
                  :scripts [{:__class__ "UziScript",
                             :arguments [{:__class__ "UziVariable", :name "en#1", :value 0},
                                         {:__class__ "UziVariable", :name "f#2", :value 0},
                                         {:__class__ "UziVariable", :name "r#3", :value 0}],
                             :delay {:__class__ "UziVariable", :value 0},
                             :instructions [{:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "en#1", :value 0}},
                                            {:__class__ "UziPopInstruction",
                                             :argument {:__class__ "UziVariable", :name "foo.enablePin", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "f#2", :value 0}},
                                            {:__class__ "UziPopInstruction",
                                             :argument {:__class__ "UziVariable", :name "foo.forwardPin", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "r#3", :value 0}},
                                            {:__class__ "UziPopInstruction",
                                             :argument {:__class__ "UziVariable", :name "foo.reversePin", :value 0}}],
                             :locals [], :name "foo.init",
                             :ticking false},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable", :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :name "foo.enablePin", :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "read"}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "retv"}}],
                             :locals [], :name "foo.getSpeed",
                             :ticking false},
                            {:__class__ "UziScript",
                             :arguments [{:__class__ "UziVariable", :name "trig#1", :value 0},
                                         {:__class__ "UziVariable", :name "echo#2", :value 0},
                                         {:__class__ "UziVariable", :name "maxDist#3", :value 0}],
                             :delay {:__class__ "UziVariable", :value 0},
                             :instructions [{:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "trig#1", :value 0}},
                                            {:__class__ "UziPopInstruction",
                                             :argument {:__class__ "UziVariable", :name "bar.trigPin", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "echo#2", :value 0}},
                                            {:__class__ "UziPopInstruction",
                                             :argument {:__class__ "UziVariable", :name "bar.echoPin", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "maxDist#3", :value 0}},
                                            {:__class__ "UziPopInstruction",
                                             :argument {:__class__ "UziVariable", :name "bar.maxDistance", :value 0}}],
                             :locals [], :name "bar.init",
                             :ticking false},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable", :value 100},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :name "bar.trigPin", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :name "bar.echoPin", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :name "bar.maxDistance", :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "sonarDistCm"}},
                                            {:__class__ "UziPopInstruction",
                                             :argument {:__class__ "UziVariable", :name "bar.distance", :value 0}}],
                             :locals [], :name "bar.reading",
                             :ticking true},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable", :value 500},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 13}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "toggle"}}],
                             :locals [], :name "blink13",
                             :ticking true},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable", :value 1000},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 12}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "toggle"}}],
                             :locals [], :name "blink12",
                             :ticking true},
                            {:__class__ "UziScript",
                             :arguments [],
                             :delay {:__class__ "UziVariable", :value 0},
                             :instructions [{:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :name "a", :value 0}},
                                            {:__class__ "UziJZInstruction",
                                             :argument 3},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 11}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "turnOn"}},
                                            {:__class__ "UziJMPInstruction",
                                             :argument 2},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 11}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "turnOff"}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :name "a", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :name "b", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :name "c", :value 0}},
                                            {:__class__ "UziScriptCallInstruction",
                                             :argument "foo_bar_baz"},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "pop"}},
                                            {:__class__ "UziStopScriptInstruction",
                                             :argument "setup"}],
                             :locals [], :name "setup",
                             :ticking true},
                            {:__class__ "UziScript",
                             :arguments [{:__class__ "UziVariable", :name "n#1", :value 0}],
                             :delay {:__class__ "UziVariable", :value 0},
                             :instructions [{:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "n#1", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "equals"}},
                                            {:__class__ "UziJZInstruction",
                                             :argument 2},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 1}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "retv"}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "n#1", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "n#1", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 1}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "subtract"}},
                                            {:__class__ "UziScriptCallInstruction",
                                             :argument "fact"},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "multiply"}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "retv"}}],
                             :locals [], :name "fact",
                             :ticking false},
                            {:__class__ "UziScript",
                             :arguments [{:__class__ "UziVariable", :name "a#1", :value 0},
                                         {:__class__ "UziVariable", :name "b#2", :value 0},
                                         {:__class__ "UziVariable", :name "c#3", :value 0}],
                             :delay {:__class__ "UziVariable", :value 0},
                             :instructions [{:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "a#1", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "b#2", :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "multiply"}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "c#3", :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "add"}},
                                            {:__class__ "UziWriteLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "d#4", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 0}},
                                            {:__class__ "UziWriteLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "@1", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "@1", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "d#4", :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "lessThan"}},
                                            {:__class__ "UziJZInstruction",
                                             :argument 7},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 16}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "toggle"}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "@1", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 1}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "add"}},
                                            {:__class__ "UziWriteLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "@1", :value 0}},
                                            {:__class__ "UziJMPInstruction",
                                             :argument -11},
                                            {:__class__ "UziStartScriptInstruction",
                                             :argument "blink13"},
                                            {:__class__ "UziStartScriptInstruction",
                                             :argument "blink12"},
                                            {:__class__ "UziStopScriptInstruction",
                                             :argument "blink13"},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "yield"}},
                                            {:__class__ "UziPauseScriptInstruction",
                                             :argument "blink12"},
                                            {:__class__ "UziPauseScriptInstruction",
                                             :argument "blink13"},
                                            {:__class__ "UziResumeScriptInstruction",
                                             :argument "blink12"},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "yield"}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "ret"}},
                                            {:__class__ "UziJMPInstruction",
                                             :argument -10},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 1}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "logicalAnd"}},
                                            {:__class__ "UziJZInstruction",
                                             :argument 5},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 10}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "toggle"}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 1000}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "delayMs"}},
                                            {:__class__ "UziJMPInstruction",
                                             :argument -9},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "logicalOr"}},
                                            {:__class__ "UziJNZInstruction",
                                             :argument 5},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 10}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "toggle"}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 1000}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "delayMs"}},
                                            {:__class__ "UziJMPInstruction",
                                             :argument -9},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 1}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "greaterThanOrEquals"}},
                                            {:__class__ "UziJNZInstruction",
                                             :argument -4},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 1}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "lessThanOrEquals"}},
                                            {:__class__ "UziJZInstruction",
                                             :argument -4},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 9}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "toggle"}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 1}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "greaterThan"}},
                                            {:__class__ "UziJNZInstruction",
                                             :argument -6},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 8}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "toggle"}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 1}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "lessThan"}},
                                            {:__class__ "UziJZInstruction",
                                             :argument -6},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 0}},
                                            {:__class__ "UziWriteLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "i#5", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "i#5", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 10}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "lessThanOrEquals"}},
                                            {:__class__ "UziJZInstruction",
                                             :argument 11},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 14}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "toggle"}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "i#5", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 100}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "multiply"}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "delayMs"}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "i#5", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 1}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "add"}},
                                            {:__class__ "UziWriteLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "i#5", :value 0}},
                                            {:__class__ "UziJMPInstruction",
                                             :argument -15},
                                            {:__class__ "UziScriptCallInstruction",
                                             :argument "foo.getSpeed"},
                                            {:__class__ "UziWriteLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "e#6", :value 0}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 1}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value -2}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "multiply"}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value -3.5}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "add"}},
                                            {:__class__ "UziScriptCallInstruction",
                                             :argument "fact"},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "a#1", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "b#2", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "d#4", :value 0}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "divide"}},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "add"}},
                                            {:__class__ "UziPushInstruction",
                                             :argument {:__class__ "UziVariable", :value 0}},
                                            {:__class__ "UziScriptCallInstruction",
                                             :argument "foo.init"},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "pop"}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "a#1", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "b#2", :value 0}},
                                            {:__class__ "UziReadLocalInstruction",
                                             :argument {:__class__ "UziVariable", :name "c#3", :value 0}},
                                            {:__class__ "UziScriptCallInstruction",
                                             :argument "bar.init"},
                                            {:__class__ "UziPrimitiveCallInstruction",
                                             :argument {:__class__ "UziPrimitive", :name "pop"}}],
                             :locals [{:__class__ "UziVariable", :name "d#4", :value 0},
                                      {:__class__ "UziVariable", :name "i#5", :value 0},
                                      {:__class__ "UziVariable", :name "e#6", :value 0},
                                      {:__class__ "UziVariable", :name "@1", :value 0}],
                             :name "foo_bar_baz",
                             :ticking false}],
                  :variables #{{:__class__ "UziVariable", :name "foo.enablePin", :value 0},
                               {:__class__ "UziVariable", :name "foo.forwardPin", :value 0},
                               {:__class__ "UziVariable", :name "foo.reversePin", :value 0},
                               {:__class__ "UziVariable", :name "bar.trigPin", :value 100},
                               {:__class__ "UziVariable", :name "bar.echoPin", :value 200},
                               {:__class__ "UziVariable", :name "bar.maxDistance", :value 100},
                               {:__class__ "UziVariable", :name "bar.distance", :value 0},
                               {:__class__ "UziVariable", :name "a", :value 10},
                               {:__class__ "UziVariable", :name "b", :value 0.5},
                               {:__class__ "UziVariable", :name "c", :value 0},
                               {:__class__ "UziVariable", :value 0},
                               {:__class__ "UziVariable", :value 100},
                               {:__class__ "UziVariable", :value 500},
                               {:__class__ "UziVariable", :value 13},
                               {:__class__ "UziVariable", :value 1000},
                               {:__class__ "UziVariable", :value 12},
                               {:__class__ "UziVariable", :value 11},
                               {:__class__ "UziVariable", :value 1},
                               {:__class__ "UziVariable", :value 16},
                               {:__class__ "UziVariable", :value 10},
                               {:__class__ "UziVariable", :value 9},
                               {:__class__ "UziVariable", :value 8},
                               {:__class__ "UziVariable", :value 14},
                               {:__class__ "UziVariable", :value -2},
                               {:__class__ "UziVariable", :value -3.5}}}
        actual (compile "
                      \"This is just an example of code that uses all the available syntax
                      in the language.\"
                      \"I wrote it to help me create a syntax highlighter for the \"\"Ace\"\" editor\"

                      import foo from 'DCMotor.uzi';
                      import bar from 'Sonar.uzi' {
                        trigPin = 100;
                        echoPin = 200;
                        stop reading;
                        start reading;
                        pause reading;
                        resume reading;
                      }

                      var a = 10;
                      var b = 0.5;
                      var c;

                      task blink13() running 2/s { toggle(D13); }
                      task blink12() running 1/s { toggle(D12); }

                      task setup() {
                          if a { turnOn(D11); }
                          else { turnOff(D11); }
                          foo_bar_baz(a, b, c);
                      }

                      func fact(n) {
                          if n == 0 { return 1; }
                          return n * fact(n - 1);
                      }

                      proc foo_bar_baz(a, b, c) {
                          var d = a * b + c;
                          repeat d { toggle(A2); }
                          forever {
                              start blink13, blink12;
                              stop blink13;
                              yield;
                              pause blink12, blink13;
                              resume blink12; yield;
                              return;
                          }
                          while 1 && 0 { toggle(D10); delayMs(1000); }
                          until 0 || 0 { toggle(D10); delayMs(1000); }
                          while 1 >= 0; \"Body is optional\"
                          until 0 <= 1; \"Body is optional\"
                          do { toggle(D9); } while 1 > 0;
                          do { toggle(D8); } until 0 < 1;
                          for i = 0 to 10 by 1 {
                              toggle(A0);
                              delayMs(i * 100);
                          }
                      	var e = foo.getSpeed();
                      	foo.init(fact(1 * -2 + -3.5), a + b/d, 0);
                      	bar.init(trig: a, echo: b, maxDist: c);
                      }")]
    (is (= expected actual))))
