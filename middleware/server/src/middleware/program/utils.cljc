(ns middleware.program.utils
  (:require [middleware.utils.core :refer [index-of]]
            [middleware.ast.primitives :as prims]
            [middleware.program.emitter :as emit]))

(def default-globals
  "These values are *always* first in the global list, whether they
   are used or not. The VM knows about this already so we don't need
   to encode them."
  (mapv emit/constant [0 1 -1]))

(defn all-globals [program]
  "Returns all the globals in the program in the correct order"
  (concat default-globals
          (remove (set default-globals)
                  (:globals program))))

(defn index-of-constant [program value]
  (index-of (all-globals program)
            (emit/constant value)))

(defn index-of-variable
  ([program name]
   (index-of (map :name (all-globals program))
             name))
  ([program name not-found]
   (let [index (index-of-variable program name)]
     (if (= -1 index) not-found index))))

(defn index-of-global ^long [program global]
  (if (contains? global :name) ; TODO(Richo): This sucks!
    (index-of-variable program (:name global))
    (index-of-constant program (:value global))))

(defn index-of-local ^long [script variable]
  (index-of (map :name (concat (:arguments script)
                               (:locals script)))
            (:name variable)))

(defn index-of-script [program script-name]
  (index-of (map :name (:scripts program))
            script-name))

(defn instructions [program]
  (mapcat :instructions (:scripts program)))

(defn pcs [program]
  (range (reduce + 0 (map (comp count :instructions)
                          (:scripts program)))))

(defn script-for-pc [{:keys [scripts]} pc]
  (loop [[script & rest] scripts
         start 0]
    (when script
      (let [stop (+ start (-> script :instructions count))]
        (if (and (>= pc start)
                 (< pc stop))
          script
          (recur rest stop))))))

(defn instruction-at-pc [program pc]
  (nth (instructions program) pc))

(defn branch? [instr]
  (contains? #{"UziJMPInstruction" "UziJZInstruction"
               "UziJNZInstruction" "UziJLTEInstruction"}
             (:__class__ instr)))

(defn unconditional-branch? [instr]
  (= "UziJMPInstruction" (:__class__ instr)))

(defn return? [instr]
  (and (= "UziPrimitiveCallInstruction" (:__class__ instr))
       (contains? #{"ret" "retv"} (-> instr :argument :name))))

(defn script-call? [instr]
  (= "UziScriptCallInstruction" (:__class__ instr)))

(defn statement? [instr]
  (case (:__class__ instr)
    ; Expressions (leave a value in the stack)
    "UziPushInstruction" false
    "UziReadLocalInstruction" false
    "UziScriptCallInstruction" false
    "UziReadInstruction" false

    ; Statements (don't leave a value on the stack)
    "UziJMPInstruction" true
    "UziJZInstruction" true
    "UziJNZInstruction" true
    "UziJLTEInstruction" true
    "UziPopInstruction" true
    "UziStartScriptInstruction" true
    "UziStopScriptInstruction" true
    "UziPauseScriptInstruction" true
    "UziResumeScriptInstruction" true
    "UziWriteLocalInstruction" true
    "UziWriteInstruction" true
    "UziTurnOnInstruction" true
    "UziTurnOffInstruction" true

    ; Special case (depends on the primitive)
    "UziPrimitiveCallInstruction"
    (let [prim (prims/primitive (-> instr :argument :name))
          [_ after] (:stack-transition prim)]
      (not= 1 after))))
