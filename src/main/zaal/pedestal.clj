(ns zaal.pedestal
  (:require [integrant.core :as ig]
            [io.pedestal.http.route :as route]
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
                          greeting   (get-in request [:query-params :name])
                          resp (greeting-for greeting name)]
                      (if resp
                        (ok {:params params :qparams qparams :bparams bparams :pparams pparams})
                        (not-found)))]
       (assoc context :response response)))})

(def graph
  {:name :graph
   :enter (fn [context]
            (let [request (:request context)
                  query (get-in request [:params :query])

                  response (pathom/pathom query)]

              (assoc context :response (ok request))))})

(defn routes [_]
  (route/expand-routes
   #{["/greet" :get respond-hello :route-name :greet]
     ["/greet/:name" :post [(muuntaja/format-interceptor) (muuntaja/params-interceptor) echo] :route-name :greet-name]
     ["/graph" :post graph :route-name :graph]}))


(defn app
  [config]
  (routes config))

(defmethod ig/init-key ::app
  [_ config]
  (println  "Starting Pedestal App")
  (let [ret (app config)]
    (println  "Started Pedestal App")
    ret))
