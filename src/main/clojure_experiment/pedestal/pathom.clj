(ns clojure-experiment.pedestal.pathom
  (:require [clojure-experiment.pedestal.datomic :as datomic]
            [clojure-experiment.pathom :as pathom]
            [cognitect.transit :as t]
            [com.wsscode.pathom3.connect.operation.transit :as pcot]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [io.pedestal.ions :as provider]
            [muuntaja.core :as m]
            [muuntaja.interceptor :as muuntaja]))


(def muuntaja-pathom-default-options
  (->
   m/default-options
   (update :default-format (constantly "application/transit+json"))
   (assoc-in
    [:formats "application/transit+json" :decoder-opts]
    {:handlers pcot/read-handlers})
   (assoc-in
    [:formats "application/transit+json" :encoder-opts]
    {:handlers  pcot/write-handlers
     :transform t/write-meta})))

(def pathom-env-interceptor
  {:name  ::pathom-env-interceptor
   :enter (fn [context]
            (update context
                    :request
                    assoc
                    ::request-fn (p.eql/boundary-interface (pathom/env {:db (get context ::datomic/db)}))))})

(def pathom
  {:name ::pathom-interceptor
   :enter (fn [context]
            (let [query (get-in context [:request :body-params])
                  request-fn (get-in context [:request ::request-fn])]
              (assoc context :response {:status 200 :body (request-fn query)})))})

(def routes
  ["/graph"
   ^:interceptors [(muuntaja/format-interceptor (m/create muuntaja-pathom-default-options))
                   (provider/datomic-params-interceptor)
                   datomic/datomic-params-interceptor
                   datomic/datomic-client-interceptor
                   datomic/datomic-conn-interceptor
                   datomic/datomic-db-interceptor
                   pathom-env-interceptor]
   {:post `pathom}])