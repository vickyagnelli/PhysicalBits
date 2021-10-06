(ns middleware.code-generator.code-generator
  (:refer-clojure :exclude [print])
  (:require [clojure.string :as str]
            [middleware.utils.core :as u]))

; TODO(Richo): Concatenating strings seems very inefficient

(defmulti print-node :__class__)

(defn print [node] (print-node node))

(defn print-optional-block [block]
  (if (empty? (:statements block))
    ";"
    (str " " (print-node block))))

(defn- remove-empty [& colls]
  (remove empty? colls))

(defmethod print-node "UziProgramNode" [node]
  (str/join (flatten
             (interpose "\n\n"
                        (remove-empty
                         (interpose "\n" (map print-node (:imports node)))
                         (interpose "\n" (map print-node (:globals node)))
                         (interpose "\n" (map print-node (:primitives node)))
                         (interpose "\n\n" (map print-node (:scripts node))))))))

(defmethod print-node "UziPrimitiveDeclarationNode" [node]
  (u/format "prim %1;"
            (if (= (:alias node) (:name node))
              (:alias node)
              (u/format "%1 : %2"
                        (:alias node)
                        (:name node)))))

(defmethod print-node "UziImportNode" [node]
  (u/format "import %1 from '%2'%3"
            (:alias node)
            (:path node)
            (print-optional-block (:initializationBlock node))))

(defmethod print-node "UziVariableDeclarationNode" [node]
  (if (:value node)
    (u/format "var %1 = %2;"
              (:name node)
              (print-node (:value node)))
    (u/format "var %1;"
              (:name node))))

(defmethod print-node "UziNumberLiteralNode" [node]
  (str (:value node)))

(defmethod print-node "UziPinLiteralNode" [node]
  (str (:type node) (:number node)))

(defmethod print-node "UziTaskNode" [node]
  (u/format "task %1()%2%3 %4"
            (:name node)
            (if (= "once" (:state node)) "" (str " " (:state node)))
            (if (nil? (:tickingRate node)) "" (print-node (:tickingRate node)))
            (print-node (:body node))))

(defmethod print-node "UziFunctionNode" [node]
  (u/format "func %1(%2) %3"
            (:name node)
            (str/join ", " (map :name (:arguments node)))
            (print-node (:body node))))

(defmethod print-node "UziProcedureNode" [node]
  (u/format "proc %1(%2) %3"
            (:name node)
            (str/join ", " (map :name (:arguments node)))
            (print-node (:body node))))

(defmethod print-node "UziTickingRateNode" [node]
  (u/format " %1/%2" (:value node) (:scale node)))

(defn add-indent-level [lines]
  (str/join (map (fn [line] (str "\t" line "\n"))
                 (filter (fn [line] (and (not= "\n" line)
                                         (not= "" line)))
                         (str/split-lines lines)))))

(defmethod print-node "UziBlockNode" [node]
  (if (empty? (:statements node))
    "{}"
    (u/format "{\n%1}"
              (add-indent-level
               (str/join "\n"
                         (map (fn [expr]
                                (if (or (str/ends-with? expr "}")
                                        (str/ends-with? expr ";"))
                                  expr
                                  (str expr ";")))
                              (map print-node (:statements node))))))))

(defn print-operator-expression [node]
  (if (= 1 (-> node :arguments count))
    ;unary
    (u/format "(%1%2)"
              (:selector node)
              (print-node (first (:arguments node))))
    ;binary
    (u/format "(%1 %2 %3)"
              (print-node (first (:arguments node)))
              (:selector node)
              (print-node (second (:arguments node))))))

(defmethod print-node "UziCallNode" [node]
  (if (nil? (re-matches #"[^a-zA-Z0-9\s\[\]\(\)\{\}\"\':#_;,]+"
                        (:selector node)))
    ;non-operator
    (u/format "%1(%2)"
              (:selector node)
              (str/join ", " (map print-node (:arguments node))))
    (print-operator-expression node)))

(defmethod print-node "Association" [node]
  (str (if (nil? (:key node)) "" (str (:key node) ": "))
       (print-node (:value node))))

(defmethod print-node "UziVariableNode" [node] (:name node))

(defmethod print-node "UziReturnNode" [node]
  (if (nil? (:value node))
    "return"
    (u/format "return %1" (print-node (:value node)))))

(defmethod print-node "UziYieldNode" [node] "yield")

(defmethod print-node "UziForNode" [node]
  (u/format "for %1 = %2 to %3 by %4 %5"
            (:name (:counter node))
            (print-node (:start node))
            (print-node (:stop node))
            (print-node (:step node))
            (print-node (:body node))))

(defmethod print-node "UziWhileNode" [node]
  (u/format "while %1%2"
            (print-node (:condition node))
            (print-optional-block (:post node))))

(defmethod print-node "UziDoWhileNode" [node]
  (u/format "do %1 while(%2)"
            (print-node (:pre node))
            (print-node (:condition node))))

(defmethod print-node "UziUntilNode" [node]
  (u/format "until %1%2"
            (print-node (:condition node))
            (print-optional-block (:post node))))

(defmethod print-node "UziDoUntilNode" [node]
  (u/format "do %1 until(%2)"
            (print-node (:pre node))
            (print-node (:condition node))))

(defmethod print-node "UziForeverNode" [node]
  (u/format "forever %1"
            (print-node (:body node))))

(defmethod print-node "UziRepeatNode" [node]
  (u/format "repeat %1 %2"
            (print-node (:times node))
            (print-node (:body node))))

(defmethod print-node "UziConditionalNode" [node]
  (let [trueBranch (u/format "if %1 %2"
                             (print-node (:condition node))
                             (print-node (:trueBranch node)))]
    (if (empty? (-> node :falseBranch :statements))
      trueBranch
      (str trueBranch " else " (print-node (:falseBranch node))))))

(defmethod print-node "UziAssignmentNode" [node]
  (u/format "%1 = %2"
            (print-node (:left node))
            (print-node (:right node))))

(defmethod print-node "UziScriptStartNode" [node]
  (u/format "start %1"
            (str/join ", " (:scripts node))))

(defmethod print-node "UziScriptStopNode" [node]
  (u/format "stop %1"
            (str/join ", " (:scripts node))))

(defmethod print-node "UziScriptPauseNode" [node]
  (u/format "pause %1"
            (str/join ", " (:scripts node))))

(defmethod print-node "UziScriptResumeNode" [node]
  (u/format "resume %1"
            (str/join ", " (:scripts node))))

(defmethod print-node "UziLogicalAndNode" [node]
  (u/format "(%1 && %2)"
            (print-node (:left node))
            (print-node (:right node))))

(defmethod print-node "UziLogicalOrNode" [node]
  (u/format "(%1 || %2)"
            (print-node (:left node))
            (print-node (:right node))))

(defmethod print-node :default [node]
  (throw (ex-info "Not Implemented node reached: " {:node node})))
