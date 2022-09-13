(ns clojure-experiment.pedestal.helpers)

(defn get-or-fail
  [m k]
  (or (get m k) (throw (ex-info (format "Key %s not found." k) {:key k
                                                                :map m}))))