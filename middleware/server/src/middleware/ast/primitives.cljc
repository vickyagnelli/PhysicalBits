(ns middleware.ast.primitives)

; TODO(Richo): This data should be written directly in the source code but the language
; doesn't allow a more useful syntax for defining primitives (yet).
(def prim-spec
 ; Prim-Name                 Code     Stack-Transition
  {"read"                   [16r0     [1     1]]
   "write"                  [16r1     [2     0]]
   "toggle"                 [16r2     [1     0]]
   "setServoDegrees"        [16r3     [2     0]]
   "servoWrite"             [16r4     [2     0]]
   "multiply"               [16r5     [2     1]]
   "add"                    [16r6     [2     1]]
   "divide"                 [16r7     [2     1]]
   "subtract"               [16r8     [2     1]]
   "seconds"                [16r9     [0     1]]
   "equals"                 [16rA     [2     1]]
   "notEquals"              [16rB     [2     1]]
   "greaterThan"            [16rC     [2     1]]
   "greaterThanOrEquals"    [16rD     [2     1]]
   "lessThan"               [16rE     [2     1]]
   "lessThanOrEquals"       [16rF     [2     1]]
   "negate"                 [16r10    [1     1]]
   "sin"                    [16r11    [1     1]]
   "cos"                    [16r12    [1     1]]
   "tan"                    [16r13    [1     1]]
   "turnOn"                 [16r14    [1     0]]
   "turnOff"                [16r15    [1     0]]
   "yield"                  [16r16    [0     0]]
   "delayMs"                [16r17    [1     0]]
   "millis"                 [16r18    [0     1]]
   "ret"                    [16r19    [0     0]]
   "pop"                    [16r1A    [1     0]]
   "retv"                   [16r1B    [1     0]]
   "coroutine"              [16r1C    [0     1]]
   "logicalAnd"             [16r1D    [2     1]]
   "logicalOr"              [16r1E    [2     1]]
   "bitwiseAnd"             [16r1F    [2     1]]
   "bitwiseOr"              [16r20    [2     1]]
   "serialWrite"            [16r21    [1     0]]
   "round"                  [16r22    [1     1]]
   "ceil"                   [16r23    [1     1]]
   "floor"                  [16r24    [1     1]]
   "sqrt"                   [16r25    [1     1]]
   "abs"                    [16r26    [1     1]]
   "ln"                     [16r27    [1     1]]
   "log10"                  [16r28    [1     1]]
   "exp"                    [16r29    [1     1]]
   "pow10"                  [16r2A    [1     1]]
   "asin"                   [16r2B    [1     1]]
   "acos"                   [16r2C    [1     1]]
   "atan"                   [16r2D    [1     1]]
   "power"                  [16r2E    [2     1]]
   "isOn"                   [16r2F    [1     1]]
   "isOff"                  [16r30    [1     1]]
   "remainder"              [16r31    [2     1]]
   "constrain"              [16r32    [3     1]]
   "randomInt"              [16r33    [2     1]]
   "random"                 [16r34    [0     1]]
   "isEven"                 [16r35    [1     1]]
   "isOdd"                  [16r36    [1     1]]
   "isPrime"                [16r37    [1     1]]
   "isWhole"                [16r38    [1     1]]
   "isPositive"             [16r39    [1     1]]
   "isNegative"             [16r3A    [1     1]]
   "isDivisibleBy"          [16r3B    [2     1]]
   "isCloseTo"              [16r3C    [2     1]]
   "delayS"                 [16r3D    [1     0]]
   "delayM"                 [16r3E    [1     0]]
   "minutes"                [16r3F    [0     1]]
   "sonarDistCm"            [16r40    [3     1]]
   "matrix8x8"              [16r41    [24    0]]
   "modulo"                 [16r42    [2     1]]
   "tone"                   [16r43    [2     0]]
   "noTone"                 [16r44    [1     0]]
   "getPinMode"             [16r45    [1     1]]
   "setPinMode"             [16r46    [2     0]]
   "atan2"                  [16r47    [2     1]]
   "getServoDegrees"        [16r48    [1     1]]
   "lcd_init0"              [16r49    [3     1]]
   "lcd_init1"              [16r50    [1     1]]
   "lcd_print"              [16r51    [3     0]]
   "array_init"             [16r52    [1     1]]
   "array_get"              [16r53    [2     1]]
   "array_set"              [16r54    [3     0]]
   "array_clear"            [16r55    [1     0]]
   "array_sum"              [16r56    [2     1]]
   "array_avg"              [16r57    [2     1]]
   "array_max"              [16r58    [2     1]]
   "array_min"              [16r59    [2     1]]
   "jmp"                    [16r60    [1     0]]
   "jz"                     [16r61    [2     0]]
   "jnz"                    [16r62    [2     0]]
   "jne"                    [16r63    [3     0]]
   "jlt"                    [16r64    [3     0]]
   "jlte"                   [16r65    [3     0]]
   "jgt"                    [16r66    [3     0]]
   "jgte"                   [16r67    [3     0]]})

(defn primitive [name]
  (when-let [data (prim-spec name)]
    {:name name :code (data 0) :stack-transition (data 1)}))
