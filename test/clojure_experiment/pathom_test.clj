(ns clojure-experiment.pathom-test
  (:require [clojure.test :refer [deftest is]]
            [clojure-experiment.pathom :as pathom]))

(deftest get-pagination-params-test
  (is (= {:limit 1 :name "Accounting"}
         (pathom/get-pagination-params {}))))