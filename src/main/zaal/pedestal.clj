(ns zaal.pedestal
  (:require [integrant.core :as ig]
            [io.pedestal.http.route :as route]))

(defn respond-hello [_]
  {:status 200 :body "Hello, world!"})

(defn routes [_]
  (route/expand-routes
   #{["/greet" :get respond-hello :route-name :greet]}))

(defn app
  [config]
  (routes config))

(defmethod ig/init-key ::app
  [_ config]
  (println  "Starting Pedestal App")
  (let [ret (app config)]
    (println  "Started Pedestal App")
    ret))
