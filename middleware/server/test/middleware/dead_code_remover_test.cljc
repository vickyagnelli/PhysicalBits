(ns middleware.dead-code-remover-test
  (:require #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest is testing use-fixtures]])
            [clojure.walk :as w]
            [utils.compile-stats :refer [register-program!]]
            [utils.tests :refer [setup-fixture]]
            [utils.equivalent :refer [equivalent?]]
            [middleware.ast.utils :as ast-utils]
            [middleware.compilation.parser :as p]
            [middleware.compilation.compiler :as cc]
            [middleware.program.emitter :as emit]
            [utils.compilation :refer [link-core]]))

(use-fixtures :once setup-fixture)

(defn compile-string [src]
  (let [ast (p/parse src)]
    (register-program! ast)
    (cc/compile-tree ast)))

(deftest
  stopped-task-with-no-refs-should-be-removed
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 0)
                              (emit/constant 1)
                              (emit/constant 11)
                              (emit/constant 13)}
                   :scripts [(emit/script
                              :name "alive1"
                              :delay 0
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 13)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])
                             (emit/script
                              :name "alive2"
                              :delay 0
                              :running? true
                              :type :task
                              :instructions [(emit/push-value 11)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                "\r\n                  task alive1() running { toggle(D13); }\r\n\t                task dead() stopped { toggle(D12); }\r\n                  task alive2() { toggle(D11); }")]
    (is (equivalent? expected actual))))

(deftest
  start-task-should-mark-the-script-as-alive
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 0) 
                              (emit/constant 1)
                              (emit/constant 13)
                              (emit/constant 12)}
                   :scripts [(emit/script
                              :name "alive"
                              :delay 0
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 13)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/start "dead")])
                             (emit/script
                              :name "dead"
                              :delay 0
                              :type :timer
                              :instructions [(emit/push-value 12)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n                \t\ttask alive() running { toggle(D13); start dead; }\r\n                \t\ttask dead() stopped { toggle(D12); }\r\n                \t\ttask reallyDead() stopped { toggle(D11); }")]
    (is (equivalent? expected actual))))

(deftest
  stop-task-should-mark-the-script-as-alive
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 0) 
                              (emit/constant 1)
                              (emit/constant 13)
                              (emit/constant 12)}
                   :scripts [(emit/script
                              :name "alive"
                              :delay 0
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 13)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/stop "dead")])
                             (emit/script
                              :name "dead"
                              :delay 0
                              :type :timer
                              :instructions [(emit/push-value 12)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n              \t\ttask alive() running { toggle(D13); stop dead; }\r\n              \t\ttask dead() stopped { toggle(D12); }\r\n              \t\ttask reallyDead() stopped { toggle(D11); }")]
    (is (equivalent? expected actual))))

(deftest
  pause-task-should-mark-the-script-as-alive
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 0)
                              (emit/constant 1)
                              (emit/constant 13)
                              (emit/constant 12)}
                   :scripts [(emit/script
                              :name "alive"
                              :delay 0
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 13)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/pause "dead")])
                             (emit/script
                              :name "dead"
                              :delay 0
                              :type :timer
                              :instructions [(emit/push-value 12)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n              \t\ttask alive() running { toggle(D13); pause dead; }\r\n              \t\ttask dead() stopped { toggle(D12); }\r\n              \t\ttask reallyDead() stopped { toggle(D11); }")]
    (is (equivalent? expected actual))))

(deftest
  resume-task-should-mark-the-script-as-alive
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 0)
                              (emit/constant 1)
                              (emit/constant 13)
                              (emit/constant 12)}
                   :scripts [(emit/script
                              :name "alive"
                              :delay 0
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 13)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/resume "dead")])
                             (emit/script
                              :name "dead"
                              :delay 0
                              :type :timer
                              :instructions [(emit/push-value 12)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n              \t\ttask alive() running { toggle(D13); resume dead; }\r\n              \t\ttask dead() stopped { toggle(D12); }\r\n              \t\ttask reallyDead() stopped { toggle(D11); }")]
    (is (equivalent? expected actual))))

(deftest
  the-visit-order-should-not-matter
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 0)
                              (emit/constant 1)
                              (emit/constant 13)
                              (emit/constant 12)}
                   :scripts [(emit/script
                              :name "dead"
                              :delay 0
                              :type :timer
                              :instructions [(emit/push-value 12) 
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])
                             (emit/script
                              :name "alive"
                              :delay 0
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 13)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/start "dead")])]))
        actual (compile-string
                 "\r\n                  task dead() stopped { toggle(D12); }\r\n              \t\ttask alive() running { toggle(D13); start dead; }\r\n              \t\ttask reallyDead() stopped { toggle(D11); }")]
    (is (equivalent? expected actual))))

(deftest
  circular-refs-should-not-be-a-problem
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 0)
                              (emit/constant 1)
                              (emit/constant 13)
                              (emit/constant 12)}
                   :scripts [(emit/script
                              :name "foo"
                              :delay 0
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 13)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/start "bar")])
                             (emit/script
                              :name "bar"
                              :delay 0
                              :type :timer
                              :instructions [(emit/start "foo")
                                             (emit/push-value 12)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n                  task foo() running { toggle(D13); start bar; }\r\n                  task bar() stopped { start foo; toggle(D12); }")]
    (is (equivalent? expected actual))))

(deftest
  stopped-script-that-starts-itself-should-not-count
  (let [expected (link-core (emit/program :globals #{} :scripts []))
        actual (compile-string
                 "task bar() stopped { start bar; toggle(D12); }")]
    (is (equivalent? expected actual))))

(deftest
  unused-globals-should-be-removed
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 1000) 
                              (emit/constant 0)
                              (emit/constant 1)
                              (emit/constant 13)}
                   :scripts [(emit/script
                              :name "blink13"
                              :delay 1000
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 13)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n                  var a = 0;\r\n                  task blink13() running 1/s { toggle(D13); }")]
    (is (equivalent? expected actual))))

(deftest
  unused-globals-should-be-removed-even-if-they-have-references-from-dead-script
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 1000)
                              (emit/constant 0)
                              (emit/constant 1)
                              (emit/constant 13)}
                   :scripts [(emit/script
                              :name "blink13"
                              :delay 1000
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 13)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n                  var a = 0;\r\n                  task blink13() running 1/s { toggle(D13); }\r\n                  task test() stopped { a = 100; }")]
    (is (equivalent? expected actual))))

(deftest 
  used-globals-should-not-be-removed
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/variable "b" 1) 
                              (emit/constant 0)
                              (emit/constant 1)
                              (emit/constant 1000)}
                   :scripts [(emit/script
                              :name "blink13"
                              :delay 1000
                              :running? true
                              :type :timer
                              :instructions [(emit/read-global "b")
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n                var a = 0;\r\n                var b = 1;\r\n                task blink13() running 1/s { toggle(b); }\r\n                task test() stopped { a = b + 1; }")]
    (is (equivalent? expected actual))))

(deftest
  unused-globals-should-be-removed-even-if-they-are-hidden-by-a-local
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 1000)
                              (emit/constant 0)
                              (emit/constant 1)
                              (emit/constant 13)}
                   :scripts [(emit/script
                              :name "blink13"
                              :delay 1000
                              :running? true
                              :type :timer
                              :locals [(emit/variable "a#1" 13)]
                              :instructions [(emit/read-local "a#1")
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n                  var a = 0;\r\n                  task blink13() running 1/s { var a = D13; toggle(a); }")]
    (is (equivalent? expected actual))))

(deftest
  calling-a-script-should-mark-it-as-alive
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 0) (emit/variable "m.reversePin" 0)
                              (emit/constant 1) (emit/variable "m.forwardPin" 0)
                              (emit/variable "m.enablePin" 0)}
                   :scripts [(emit/script
                              :name "m.forward"
                              :arguments [(emit/variable "speed#1" 0)]
                              :delay 0
                              :type :procedure
                              :instructions [(emit/read-global "m.reversePin")
                                             (emit/push-value 0)
                                             (emit/prim-call "write")
                                             (emit/read-global "m.forwardPin")
                                             (emit/push-value 1)
                                             (emit/prim-call "write")
                                             (emit/read-global "m.enablePin")
                                             (emit/read-local "speed#1")
                                             (emit/prim-call "write")])
                             (emit/script
                              :name "loop"
                              :delay 0
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 1)
                                             (emit/script-call "m.forward")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n                  import m from 'DCMotor.uzi';\r\n                  task loop() running { m.forward(1); }")]
    (is (equivalent? expected actual))))

(deftest
  a-more-complete-example
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 0)
                              (emit/constant 1)
                              (emit/constant 11)
                              (emit/constant 1000)
                              (emit/constant 13)
                              (emit/constant 500)
                              (emit/constant 12)
                              (emit/variable "a" 10)}
                   :scripts [(emit/script
                              :name "blink13"
                              :delay 500
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 13) 
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])
                             (emit/script
                              :name "blink12"
                              :delay 1000
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 12) 
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])
                             (emit/script
                              :name "setup"
                              :delay 0
                              :running? true
                              :type :task
                              :instructions [(emit/read-global "a")
                                             (emit/jz 3)
                                             (emit/push-value 11)
                                             (emit/script-call "turnOn")
                                             #_(emit/prim-call "pop")
                                             (emit/jmp 2)
                                             (emit/push-value 11)
                                             (emit/script-call "turnOff")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n                  \"This is just an example of code that uses all the available syntax\r\n                  in the language.\"\r\n                  \"I wrote it to help me create a syntax highlighter for the \"\"Ace\"\" editor\"\r\n\r\n                  import foo from 'DCMotor.uzi';\r\n                  import bar from 'Sonar.uzi' {\r\n                    trigPin = 100;\r\n                    echoPin = 200;\r\n                    start reading;\r\n                    stop reading;\r\n                    resume reading;\r\n                    pause reading;\r\n                  }\r\n\r\n                  var a = 10;\r\n                  var b = 0.5;\r\n                  var c;\r\n\r\n                  task blink13() running 2/s { toggle(D13); }\r\n                  task blink12() running 1/s { toggle(D12); }\r\n\r\n                  task setup() {\r\n                    if a { turnOn(D11); }\r\n                    else { turnOff(D11); }\r\n                  }\r\n\r\n                  func fact(n) {\r\n                    if n == 0 { return 1; }\r\n                    return n * fact(n - 1);\r\n                  }\r\n\r\n                  proc foo_bar_baz(a, b, c) {\r\n                    var d = a * b + c;\r\n                    repeat d { toggle(A2); }\r\n                    forever {\r\n                      start blink13, blink12;\r\n                      stop blink13;\r\n                      yield;\r\n                      pause blink12, blink13;\r\n                      resume blink12; yield;\r\n                      return;\r\n                    }\r\n                    while 1 && 0 { toggle(D10); delayMs(1000); }\r\n                    until 0 || 0 { toggle(D10); delayMs(1000); }\r\n                    while 1 >= 0; \"Body is optional\"\r\n                    until 0 <= 1; \"Body is optional\"\r\n                    do { toggle(D9); } while 1 > 0;\r\n                    do { toggle(D8); } until 0 < 1;\r\n                    for i = 0 to 10 by 1 {\r\n                      toggle(A0);\r\n                      delayMs(i * 100);\r\n                    }\r\n                    var e = foo.getSpeed();\r\n                    foo.init(fact(1 * -2 + -3.5), a + b/d, 0);\r\n                    bar.init(trig: a, echo: b, maxDist: c);\r\n                  }")]
    (is (equivalent? expected actual))))

(deftest
  a-more-complete-example-2
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 0) (emit/variable "c" 0)
                              (emit/constant 9) (emit/constant -2)
                              (emit/variable "foo.forwardPin" 0)
                              (emit/variable "bar.trigPin" 100)
                              (emit/constant 11)
                              (emit/variable "bar.echoPin" 200)
                              (emit/variable "foo.reversePin" 0)
                              (emit/constant 100)
                              (emit/variable "bar.distance" 0)
                              (emit/constant 1000) (emit/constant 1)
                              (emit/constant 13) (emit/variable "b" 0.5)
                              (emit/constant 16) (emit/constant 10)
                              (emit/variable "foo.enablePin" 0)
                              (emit/constant 8) (emit/constant 14)
                              (emit/constant 500) (emit/constant -3.5)
                              (emit/constant 12)
                              (emit/variable "bar.maxDistance" 100)
                              (emit/variable "a" 10)}
                   :scripts [(emit/script
                              :name "foo.init"
                              :arguments [(emit/variable "en#1" 0)
                                          (emit/variable "f#2" 0)
                                          (emit/variable "r#3" 0)]
                              :delay 0
                              :type :procedure
                              :instructions [(emit/read-local "en#1")
                                             (emit/write-global "foo.enablePin")
                                             (emit/read-local "f#2")
                                             (emit/write-global "foo.forwardPin")
                                             (emit/read-local "r#3")
                                             (emit/write-global "foo.reversePin")])
                             (emit/script
                              :name "foo.getSpeed"
                              :delay 0                              
                              :type :function
                              :instructions [(emit/read-global "foo.enablePin")
                                             (emit/prim-call "read")
                                             (emit/prim-call "retv")])
                             (emit/script
                              :name "bar.init"
                              :arguments [(emit/variable "trig#1" 0)
                                          (emit/variable "echo#2" 0)
                                          (emit/variable "maxDist#3" 0)]
                              :delay 0
                              :type :procedure
                              :instructions [(emit/read-local "trig#1")
                                             (emit/write-global "bar.trigPin")
                                             (emit/read-local "echo#2")
                                             (emit/write-global "bar.echoPin")
                                             (emit/read-local "maxDist#3")
                                             (emit/write-global "bar.maxDistance")])
                             (emit/script
                              :name "bar.reading"
                              :delay 100
                              :running? true
                              :type :timer
                              :instructions [(emit/read-global "bar.trigPin")
                                             (emit/read-global "bar.echoPin")
                                             (emit/read-global "bar.maxDistance")
                                             (emit/prim-call "sonarDistCm")
                                             (emit/write-global "bar.distance")])
                             (emit/script
                              :name "blink13"
                              :delay 500
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 13) 
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])
                             (emit/script
                              :name "blink12"
                              :delay 1000
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 12) 
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])
                             (emit/script
                              :name "setup"
                              :delay 0
                              :running? true
                              :type :task
                              :instructions [(emit/read-global "a")
                                             (emit/jz 3)
                                             (emit/push-value 11)
                                             (emit/script-call "turnOn")
                                             #_(emit/prim-call "pop")
                                             (emit/jmp 2)
                                             (emit/push-value 11)
                                             (emit/script-call "turnOff")
                                             #_(emit/prim-call "pop")
                                             (emit/read-global "a")
                                             (emit/read-global "b")
                                             (emit/read-global "c")
                                             (emit/script-call "foo_bar_baz")
                                             #_(emit/prim-call "pop")])
                             (emit/script
                              :name "fact"
                              :arguments [(emit/variable "n#1" 0)]
                              :delay 0
                              :type :function
                              :instructions [(emit/read-local "n#1")
                                             (emit/push-value 0)
                                             (emit/prim-call "equals")
                                             (emit/jz 2)
                                             (emit/push-value 1)
                                             (emit/prim-call "retv")
                                             (emit/read-local "n#1")
                                             (emit/read-local "n#1")
                                             (emit/push-value 1)
                                             (emit/prim-call "subtract")
                                             (emit/script-call "fact")
                                             (emit/prim-call "multiply")
                                             (emit/prim-call "retv")])
                             (emit/script
                              :name "foo_bar_baz"
                              :arguments [(emit/variable "a#1" 0)
                                          (emit/variable "b#2" 0)
                                          (emit/variable "c#3" 0)]
                              :delay 0                              
                              :type :procedure
                              :locals [(emit/variable "d#4" 0)
                                       (emit/variable "i#5" 0)
                                       (emit/variable "e#6" 0)
                                       (emit/variable "@1" 0)]
                              :instructions [(emit/read-local "a#1")
                                             (emit/read-local "b#2")
                                             (emit/prim-call "multiply")
                                             (emit/read-local "c#3")
                                             (emit/prim-call "add")
                                             (emit/write-local "d#4")
                                             (emit/push-value 0)
                                             (emit/write-local "@1")
                                             (emit/read-local "@1")
                                             (emit/read-local "d#4")
                                             (emit/prim-call "lessThan")
                                             (emit/jz 7)
                                             (emit/push-value 16)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/read-local "@1")
                                             (emit/push-value 1)
                                             (emit/prim-call "add")
                                             (emit/write-local "@1")
                                             (emit/jmp -11)
                                             (emit/start "blink13")
                                             (emit/start "blink12")
                                             (emit/stop "blink13")
                                             (emit/prim-call "yield")
                                             (emit/pause "blink12")
                                             (emit/pause "blink13")
                                             (emit/resume "blink12")
                                             (emit/prim-call "yield")
                                             (emit/prim-call "ret")
                                             (emit/jmp -10)
                                             (emit/push-value 1)
                                             (emit/push-value 0)
                                             (emit/prim-call "logicalAnd")
                                             (emit/jz 5)
                                             (emit/push-value 10)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/push-value 1000)
                                             (emit/prim-call "delayMs")
                                             (emit/jmp -9)
                                             (emit/push-value 0)
                                             (emit/push-value 0)
                                             (emit/prim-call "logicalOr")
                                             (emit/jnz 5)
                                             (emit/push-value 10)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/push-value 1000)
                                             (emit/prim-call "delayMs")
                                             (emit/jmp -9)
                                             (emit/push-value 1)
                                             (emit/push-value 0)
                                             (emit/prim-call "greaterThanOrEquals")
                                             (emit/jnz -4)
                                             (emit/push-value 0)
                                             (emit/push-value 1)
                                             (emit/prim-call "lessThanOrEquals")
                                             (emit/jz -4)
                                             (emit/push-value 9)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/push-value 1)
                                             (emit/push-value 0)
                                             (emit/prim-call "greaterThan")
                                             (emit/jnz -6)
                                             (emit/push-value 8)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/push-value 0)
                                             (emit/push-value 1)
                                             (emit/prim-call "lessThan")
                                             (emit/jz -6)
                                             (emit/push-value 0)
                                             (emit/write-local "i#5")
                                             (emit/read-local "i#5")
                                             (emit/push-value 10)
                                             (emit/prim-call "lessThanOrEquals")
                                             (emit/jz 11)
                                             (emit/push-value 14)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/read-local "i#5")
                                             (emit/push-value 100)
                                             (emit/prim-call "multiply")
                                             (emit/prim-call "delayMs")
                                             (emit/read-local "i#5")
                                             (emit/push-value 1)
                                             (emit/prim-call "add")
                                             (emit/write-local "i#5")
                                             (emit/jmp -15)
                                             (emit/script-call "foo.getSpeed")
                                             (emit/write-local "e#6")
                                             (emit/push-value 1)
                                             (emit/push-value -2)
                                             (emit/prim-call "multiply")
                                             (emit/push-value -3.5)
                                             (emit/prim-call "add")
                                             (emit/script-call "fact")
                                             (emit/read-local "a#1")
                                             (emit/read-local "b#2")
                                             (emit/read-local "d#4")
                                             (emit/prim-call "divide")
                                             (emit/prim-call "add")
                                             (emit/push-value 0)
                                             (emit/script-call "foo.init")
                                             #_(emit/prim-call "pop")
                                             (emit/read-local "a#1")
                                             (emit/read-local "b#2")
                                             (emit/read-local "c#3")
                                             (emit/script-call "bar.init")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n                      \"This is just an example of code that uses all the available syntax\r\n                      in the language.\"\r\n                      \"I wrote it to help me create a syntax highlighter for the \"\"Ace\"\" editor\"\r\n\r\n                      import foo from 'DCMotor.uzi';\r\n                      import bar from 'Sonar.uzi' {\r\n                        trigPin = 100;\r\n                        echoPin = 200;\r\n                        stop reading;\r\n                        start reading;\r\n                        pause reading;\r\n                        resume reading;\r\n                      }\r\n\r\n                      var a = 10;\r\n                      var b = 0.5;\r\n                      var c;\r\n\r\n                      task blink13() running 2/s { toggle(D13); }\r\n                      task blink12() running 1/s { toggle(D12); }\r\n\r\n                      task setup() {\r\n                          if a { turnOn(D11); }\r\n                          else { turnOff(D11); }\r\n                          foo_bar_baz(a, b, c);\r\n                      }\r\n\r\n                      func fact(n) {\r\n                          if n == 0 { return 1; }\r\n                          return n * fact(n - 1);\r\n                      }\r\n\r\n                      proc foo_bar_baz(a, b, c) {\r\n                          var d = a * b + c;\r\n                          repeat d { toggle(A2); }\r\n                          forever {\r\n                              start blink13, blink12;\r\n                              stop blink13;\r\n                              yield;\r\n                              pause blink12, blink13;\r\n                              resume blink12; yield;\r\n                              return;\r\n                          }\r\n                          while 1 && 0 { toggle(D10); delayMs(1000); }\r\n                          until 0 || 0 { toggle(D10); delayMs(1000); }\r\n                          while 1 >= 0; \"Body is optional\"\r\n                          until 0 <= 1; \"Body is optional\"\r\n                          do { toggle(D9); } while 1 > 0;\r\n                          do { toggle(D8); } until 0 < 1;\r\n                          for i = 0 to 10 by 1 {\r\n                              toggle(A0);\r\n                              delayMs(i * 100);\r\n                          }\r\n                      \tvar e = foo.getSpeed();\r\n                      \tfoo.init(fact(1 * -2 + -3.5), a + b/d, 0);\r\n                      \tbar.init(trig: a, echo: b, maxDist: c);\r\n                      }")]
    (is (equivalent? expected actual))))

(deftest
  a-more-complete-example-3
  (let [expected (link-core
                  (emit/program
                   :globals #{(emit/constant 0) (emit/constant 9)
                              (emit/constant -2) (emit/constant 2)
                              (emit/variable "foo.forwardPin" 0)
                              (emit/variable "bar.trigPin" 100)
                              (emit/constant 11)
                              (emit/variable "bar.echoPin" 200)
                              (emit/variable "foo.reversePin" 0)
                              (emit/constant 100)
                              (emit/variable "bar.distance" 0) (emit/constant 3)
                              (emit/constant 1000) (emit/constant 1)
                              (emit/constant 13) (emit/constant 16)
                              (emit/constant 10)
                              (emit/variable "foo.enablePin" 0)
                              (emit/constant 8) (emit/constant 14)
                              (emit/constant 500) (emit/constant -3.5)
                              (emit/constant 12)
                              (emit/variable "bar.maxDistance" 100)
                              (emit/variable "a" 10)}
                   :scripts [(emit/script
                              :name "foo.init"
                              :arguments [(emit/variable "en#1" 0)
                                          (emit/variable "f#2" 0)
                                          (emit/variable "r#3" 0)]
                              :delay 0
                              :type :procedure
                              :instructions [(emit/read-local "en#1")
                                             (emit/write-global "foo.enablePin")
                                             (emit/read-local "f#2")
                                             (emit/write-global "foo.forwardPin")
                                             (emit/read-local "r#3")
                                             (emit/write-global "foo.reversePin")])
                             (emit/script
                              :name "foo.getSpeed"
                              :delay 0
                              :type :function
                              :instructions [(emit/read-global "foo.enablePin")
                                             (emit/prim-call "read")
                                             (emit/prim-call "retv")])
                             (emit/script
                              :name "bar.init"
                              :arguments [(emit/variable "trig#1" 0)
                                          (emit/variable "echo#2" 0)
                                          (emit/variable "maxDist#3" 0)]
                              :delay 0
                              :type :procedure
                              :instructions [(emit/read-local "trig#1")
                                             (emit/write-global "bar.trigPin")
                                             (emit/read-local "echo#2")
                                             (emit/write-global "bar.echoPin")
                                             (emit/read-local "maxDist#3")
                                             (emit/write-global "bar.maxDistance")])
                             (emit/script
                              :name "bar.reading"
                              :delay 100
                              :running? true
                              :type :timer
                              :instructions [(emit/read-global "bar.trigPin")
                                             (emit/read-global "bar.echoPin")
                                             (emit/read-global "bar.maxDistance")
                                             (emit/prim-call "sonarDistCm")
                                             (emit/write-global "bar.distance")])
                             (emit/script
                              :name "blink13"
                              :delay 500
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 13) 
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])
                             (emit/script
                              :name "blink12"
                              :delay 1000
                              :running? true
                              :type :timer
                              :instructions [(emit/push-value 12) 
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")])
                             (emit/script
                              :name "setup"
                              :delay 0
                              :running? true
                              :type :task
                              :instructions [(emit/read-global "a")
                                             (emit/jz 3)
                                             (emit/push-value 11)
                                             (emit/script-call "turnOn")
                                             #_(emit/prim-call "pop")
                                             (emit/jmp 2)
                                             (emit/push-value 11)
                                             (emit/script-call "turnOff")
                                             #_(emit/prim-call "pop")
                                             (emit/push-value 1)
                                             (emit/push-value 2)
                                             (emit/push-value 3)
                                             (emit/script-call "foo_bar_baz")
                                             #_(emit/prim-call "pop")])
                             (emit/script
                              :name "fact"
                              :arguments [(emit/variable "n#1" 0)]
                              :delay 0
                              :type :function
                              :instructions [(emit/read-local "n#1")
                                             (emit/push-value 0)
                                             (emit/prim-call "equals")
                                             (emit/jz 2)
                                             (emit/push-value 1)
                                             (emit/prim-call "retv")
                                             (emit/read-local "n#1")
                                             (emit/read-local "n#1")
                                             (emit/push-value 1)
                                             (emit/prim-call "subtract")
                                             (emit/script-call "fact")
                                             (emit/prim-call "multiply")
                                             (emit/prim-call "retv")])
                             (emit/script
                              :name "foo_bar_baz"
                              :arguments [(emit/variable "a#1" 0)
                                          (emit/variable "b#2" 0)
                                          (emit/variable "c#3" 0)]
                              :delay 0
                              :type :procedure
                              :locals [(emit/variable "d#4" 0)
                                       (emit/variable "i#5" 0)
                                       (emit/variable "e#6" 0)
                                       (emit/variable "@1" 0)]
                              :instructions [(emit/read-local "a#1")
                                             (emit/read-local "b#2")
                                             (emit/prim-call "multiply")
                                             (emit/read-local "c#3")
                                             (emit/prim-call "add")
                                             (emit/write-local "d#4")
                                             (emit/push-value 0)
                                             (emit/write-local "@1")
                                             (emit/read-local "@1")
                                             (emit/read-local "d#4")
                                             (emit/prim-call "lessThan")
                                             (emit/jz 7)
                                             (emit/push-value 16)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/read-local "@1")
                                             (emit/push-value 1)
                                             (emit/prim-call "add")
                                             (emit/write-local "@1")
                                             (emit/jmp -11)
                                             (emit/start "blink13")
                                             (emit/start "blink12")
                                             (emit/stop "blink13")
                                             (emit/prim-call "yield")
                                             (emit/pause "blink12")
                                             (emit/pause "blink13")
                                             (emit/resume "blink12")
                                             (emit/prim-call "yield")
                                             (emit/prim-call "ret")
                                             (emit/jmp -10)
                                             (emit/push-value 1)
                                             (emit/push-value 0)
                                             (emit/prim-call "logicalAnd")
                                             (emit/jz 5)
                                             (emit/push-value 10)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/push-value 1000)
                                             (emit/prim-call "delayMs")
                                             (emit/jmp -9)
                                             (emit/push-value 0)
                                             (emit/push-value 0)
                                             (emit/prim-call "logicalOr")
                                             (emit/jnz 5)
                                             (emit/push-value 10)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/push-value 1000)
                                             (emit/prim-call "delayMs")
                                             (emit/jmp -9)
                                             (emit/push-value 1)
                                             (emit/push-value 0)
                                             (emit/prim-call "greaterThanOrEquals")
                                             (emit/jnz -4)
                                             (emit/push-value 0)
                                             (emit/push-value 1)
                                             (emit/prim-call "lessThanOrEquals")
                                             (emit/jz -4)
                                             (emit/push-value 9)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/push-value 1)
                                             (emit/push-value 0)
                                             (emit/prim-call "greaterThan")
                                             (emit/jnz -6)
                                             (emit/push-value 8)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/push-value 0)
                                             (emit/push-value 1)
                                             (emit/prim-call "lessThan")
                                             (emit/jz -6)
                                             (emit/push-value 0)
                                             (emit/write-local "i#5")
                                             (emit/read-local "i#5")
                                             (emit/push-value 10)
                                             (emit/prim-call "lessThanOrEquals")
                                             (emit/jz 11)
                                             (emit/push-value 14)
                                             (emit/script-call "toggle")
                                             #_(emit/prim-call "pop")
                                             (emit/read-local "i#5")
                                             (emit/push-value 100)
                                             (emit/prim-call "multiply")
                                             (emit/prim-call "delayMs")
                                             (emit/read-local "i#5")
                                             (emit/push-value 1)
                                             (emit/prim-call "add")
                                             (emit/write-local "i#5")
                                             (emit/jmp -15)
                                             (emit/script-call "foo.getSpeed")
                                             (emit/write-local "e#6")
                                             (emit/push-value 1)
                                             (emit/push-value -2)
                                             (emit/prim-call "multiply")
                                             (emit/push-value -3.5)
                                             (emit/prim-call "add")
                                             (emit/script-call "fact")
                                             (emit/read-local "a#1")
                                             (emit/read-local "b#2")
                                             (emit/read-local "d#4")
                                             (emit/prim-call "divide")
                                             (emit/prim-call "add")
                                             (emit/push-value 0)
                                             (emit/script-call "foo.init")
                                             #_(emit/prim-call "pop")
                                             (emit/read-local "a#1")
                                             (emit/read-local "b#2")
                                             (emit/read-local "c#3")
                                             (emit/script-call "bar.init")
                                             #_(emit/prim-call "pop")])]))
        actual (compile-string
                 "\r\n                  \"This is just an example of code that uses all the available syntax\r\n                  in the language.\"\r\n                  \"I wrote it to help me create a syntax highlighter for the \"\"Ace\"\" editor\"\r\n\r\n                  import foo from 'DCMotor.uzi';\r\n                  import bar from 'Sonar.uzi' {\r\n                    trigPin = 100;\r\n                    echoPin = 200;\r\n                    stop reading;\r\n                    start reading;\r\n                    pause reading;\r\n                    resume reading;\r\n                  }\r\n\r\n                  var a = 10;\r\n                  var b = 0.5;\r\n                  var c;\r\n\r\n                  task blink13() running 2/s { toggle(D13); }\r\n                  task blink12() running 1/s { toggle(D12); }\r\n\r\n                  task setup() {\r\n                      if a { turnOn(D11); }\r\n                      else { turnOff(D11); }\r\n                      foo_bar_baz(1, 2, 3);\r\n                  }\r\n\r\n                  func fact(n) {\r\n                      if n == 0 { return 1; }\r\n                      return n * fact(n - 1);\r\n                  }\r\n\r\n                  proc foo_bar_baz(a, b, c) {\r\n                      var d = a * b + c;\r\n                      repeat d { toggle(A2); }\r\n                      forever {\r\n                          start blink13, blink12;\r\n                          stop blink13;\r\n                          yield;\r\n                          pause blink12, blink13;\r\n                          resume blink12; yield;\r\n                          return;\r\n                      }\r\n                      while 1 && 0 { toggle(D10); delayMs(1000); }\r\n                      until 0 || 0 { toggle(D10); delayMs(1000); }\r\n                      while 1 >= 0; \"Body is optional\"\r\n                      until 0 <= 1; \"Body is optional\"\r\n                      do { toggle(D9); } while 1 > 0;\r\n                      do { toggle(D8); } until 0 < 1;\r\n                      for i = 0 to 10 by 1 {\r\n                          toggle(A0);\r\n                          delayMs(i * 100);\r\n                      }\r\n                  \tvar e = foo.getSpeed();\r\n                  \tfoo.init(fact(1 * -2 + -3.5), a + b/d, 0);\r\n                  \tbar.init(trig: a, echo: b, maxDist: c);\r\n                  }")]
    (is (equivalent? expected actual))))
