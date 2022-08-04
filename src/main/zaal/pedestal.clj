(ns zaal.pedestal
  (:require [com.wsscode.pathom3.connect.operation.transit :as pcot]
            [cognitect.transit :as t]
            [integrant.core :as ig]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route :as route]
            [muuntaja.core :as m]
            [muuntaja.interceptor :as muuntaja]
            [zaal.pathom :as pathom]))

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
      (ok (pathom/pathom [:acme.math/pi]))
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

(def graph
  {:name :graph
   :enter (fn [context]
            (let [request (:request context)
                  query (get-in request [:params :query])
                  response (->> query (m/decode "application/edn"))]

              (assoc context :response (ok (pathom/pathom response)))))})

(def pathom-format-interceptor-defaults
  (->
   m/default-options
   (update :default-format (constantly "application/edn"))
   (assoc-in
    [:formats "application/transit+json" :decoder-opts]
    {:handlers pcot/read-handlers})
   (assoc-in
    [:formats "application/transit+json" :encoder-opts]
    {:handlers  pcot/write-handlers
     :transform t/write-meta})))

(defn routes [_]
  (route/expand-routes
   #{["/greet" :get respond-hello :route-name :greet]
     ["/greet/:name" :post [(muuntaja/format-interceptor) (muuntaja/params-interceptor) echo] :route-name :greet-name]
     ["/graph" :post [(muuntaja/format-interceptor) (muuntaja/params-interceptor) graph] :route-name :graph]
     ["/graph2" :post [(body-params/body-params
                        (body-params/default-parser-map :transit-options {:handlers pcot/read-handlers}))
                       (http/transit-body-interceptor
                        ::http/transit-json-body
                        "application/transit+json"
                        :json
                        {:handlers pcot/write-handlers}) graph] :route-name :graph2]}))


(defn app
  [config]
  (routes config))

(defmethod ig/init-key ::app
  [_ config]
  (println  "Starting Pedestal App")
  (let [ret (app config)]
    (println  "Started Pedestal App")
    ret))
