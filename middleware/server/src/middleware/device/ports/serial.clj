(ns middleware.device.ports.serial
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as a :refer [<! go timeout]]
            [clojure.string :as str]
            [serial.core :as s]
            [middleware.device.ports.common :as ports]))

(extend-type serial.core.Port
  ports/UziPort
  (close! [port] (s/close! port))
  (make-out-chan! [port]
                  (let [out-chan (a/chan 1000)]
                    (go
                     (try
                       (loop []
                         (when-let [data (<! out-chan)]
                           (s/write port data)
                           (recur)))
                       (catch Throwable ex
                         (log/error "ERROR WHILE WRITING OUTPUT (serial) ->" ex)
                         (a/close! out-chan))))
                    out-chan))
  (make-in-chan! [port]
                 (let [in-chan (a/chan (a/sliding-buffer 1000))]
                   (s/listen! port
                              (fn [^java.io.InputStream input-stream]
                                (try
                                  (a/put! in-chan (.read input-stream))
                                  (catch Throwable ex
                                    (log/error "ERROR WHILE READING INPUT (serial) ->" ex)
                                    (a/close! in-chan)))))
                   in-chan)))

(defn open-port [port-name baud-rate]
  (try
    (s/open port-name :baud-rate baud-rate)
    (catch Throwable ex
      (do (log/error "ERROR WHILE OPENING PORT (serial) ->" ex) nil))))
