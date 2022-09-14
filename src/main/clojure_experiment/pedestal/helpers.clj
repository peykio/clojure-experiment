(ns clojure-experiment.pedestal.helpers)

(defn get-or-fail
  [m k]
  (or (get m k) (if (nil? m)
                  (throw (ex-info "Parameter map is nil. Please make sure `io.pedestal.ions/datomic-params-interceptor` is in the interceptor chain." {:key k
                                                                                                                                                       :map m}))
                  (throw (ex-info (format "Key %s not found." k) {:key k
                                                                  :map m})))))