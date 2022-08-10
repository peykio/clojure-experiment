(ns clojure-experiment.pedestal
  (:require [cognitect.transit :as t]
            [com.wsscode.pathom3.connect.operation.transit :as pcot]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [integrant.core :as ig]
            [io.pedestal.http.route :as route]
            [muuntaja.core :as m]
            [muuntaja.interceptor :as muuntaja]))

(def unmentionables #{"YHWH" "Voldemort" "Mxyzptlk" "Rumplestiltskin" "曹操"})

(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok       (partial response 200))
(def created  (partial response 201))
(def accepted (partial response 202))

(defn not-found []
  {:status 404 :body "Not found\n"})

(defn greeting-for [greeting name]
  (cond
    (unmentionables name) nil
    (empty? name)         "Hello, world!\n"
    :else               {:response (str greeting " ! " name)}))

(defn respond-hello [request]
  (let [name   (get-in request [:path-params :name])
        greeting   (get-in request [:query-params :name])
        resp (greeting-for greeting name)]
    (if resp
      (ok resp)
      (not-found))))

(def echo
  {:name :echo
   :enter
   (fn [context]
     (let [request (:request context)
           response (let [name   (get-in request [:path-params :name])
                          qparams   (get-in request [:query-params])
                          pparams   (get-in request [:path-params])
                          bparams   (get-in request [:body-params])
                          params   (get-in request [:params])
                          greeting   (get-in request [:query-params :greeting])
                          greeting-json   (->> (get-in request [:query-params :greeting]) (m/decode "application/json"))
                          query-json   (->> (get-in request [:params :query]) (m/decode "application/edn"))
                          resp (greeting-for greeting name)]
                      (if resp
                        (ok {:params params :qparams qparams :bparams bparams :pparams pparams :greet resp :json greeting-json :edn query-json})
                        (not-found)))]
       (assoc context :response response)))})


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
   #{["/greet" :get respond-hello :route-name :greet]
     ["/greet/:name" :post [(muuntaja/format-interceptor) (muuntaja/params-interceptor) echo] :route-name :greet-name]
     ["/graph" :post [(muuntaja/format-interceptor (m/create pathom-format-interceptor-defaults))
                      (pathom-env-interceptor pathom-env)
                      graph] :route-name :graph]}))

(defmethod ig/init-key ::app
  [_ config]
  (println  "Starting Pedestal App")
  (let [ret (routes config)]
    (println  "Started Pedestal App")
    ret))