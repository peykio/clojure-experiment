(ns zaal.pedestal
  (:require [integrant.core :as ig]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route :as route]))

(defn respond-hello [_]
  {:status 200 :body "Hello, world!"})

(def common-interceptors [(body-params/body-params) http/json-body])
(defn routes [_]
  (route/expand-routes
   #{["/greet" :get (conj common-interceptors `respond-hello) :route-name :greet]
     ["/recipes" :get respond-hello :route-name :recipes]}))

(defn app
  [config]
  (routes config))

(defmethod ig/init-key ::app
  [_ config]
  (println  "Starting Pedestal App")
  (let [ret (app config)]
    (println  "Started Pedestal App")
    ret))
