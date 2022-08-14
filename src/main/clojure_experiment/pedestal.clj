(ns clojure-experiment.pedestal
  (:require [cognitect.transit :as t]
            [com.wsscode.pathom3.connect.operation.transit :as pcot]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [integrant.core :as ig]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [muuntaja.core :as m]
            [muuntaja.interceptor :as muuntaja]))


(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok (partial response 200))

(def pathom-format-interceptor-defaults
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
                  (fn? request-or-env)
                  request-or-env

                  (map? request-or-env)
                  (p.eql/boundary-interface request-or-env)

                  :else
                  (throw (ex-info "Invalid input to start server, must send an env map or a boundary interface fn"
                                  {})))]
    {:name  ::pathom-env-interceptor
     :enter (fn [context]
              (update context :request assoc ::request-fn request))}))

(def graph
  {:name :graph
   :enter (fn [context]
            (let [query (get-in context [:request :body-params])
                  request-fn (get-in context [:request ::request-fn])]
              (assoc context :response (ok (request-fn query)))))})


(defn routes [{:keys [pathom-env]}]
  (route/expand-routes
   #{["/graph" :post [(muuntaja/format-interceptor (m/create pathom-format-interceptor-defaults))
                      (pathom-env-interceptor pathom-env)
                      graph] :route-name :graph]}))

(defmethod ig/init-key ::routes
  [_ config]
  (routes config))