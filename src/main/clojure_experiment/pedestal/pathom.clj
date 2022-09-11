(ns clojure-experiment.pedestal.pathom
  (:require [cognitect.transit :as t]
            [com.wsscode.pathom3.connect.operation.transit :as pcot]
            [com.wsscode.pathom3.interface.eql :as p.eql]
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

(defn pathom-env-interceptor [request-or-env]
  (let [request (cond
                  (fn? request-or-env) request-or-env
                  (map? request-or-env) (p.eql/boundary-interface request-or-env)
                  :else
                  (throw (ex-info "Invalid input to start server, must send an env map or a boundary interface fn" {})))]
    {:name  ::pathom-env-interceptor
     :enter (fn [context]
              (update context :request assoc ::request-fn request))}))

(def pathom-interceptor
  {:name ::pathom-interceptor
   :enter (fn [context]
            (let [query (get-in context [:request :body-params])
                  request-fn (get-in context [:request ::request-fn])]
              (assoc context :response {:status 200 :body (request-fn query)})))})

(defn routes [{:keys [pathom-env]}]
  ["/graph"
   ^:interceptors [(muuntaja/format-interceptor (m/create muuntaja-pathom-default-options))
                   (pathom-env-interceptor pathom-env)]
   {:post `pathom-interceptor}])