(ns clojure-experiment.pathom-test
  (:require [clojure.test :refer [deftest]]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.gen.alpha :as gen]
            [clojure.pprint :as pp]
            [clojure-experiment.pathom :as pathom]))

(deftest coax-pagination-params
  (stest/summarize-results (stest/check `pathom/coax-pagination-params)))

(comment
  (pp/pprint
   (stest/check `num-sort))
  (pp/pprint (stest/check `pathom/coax-pagination-params))
  (pp/pprint
   (gen/sample (s/gen :clojure-experiment.pathom/params) 10))
  (gen/sample (s/gen (s/every-kv
                      #{:limit :offset :extra-key}
                      (s/or :string string? :int int?))) 10)
  (stest/summarize-results (stest/check `pathom/coax-pagination-params))
  (stest/summarize-results (stest/check `pathom/decode-pagination-params)))



