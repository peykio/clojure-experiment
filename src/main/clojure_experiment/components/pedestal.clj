(ns clojure-experiment.components.pedestal
  (:require [integrant.core :as ig]
            [io.pedestal.http :as http]))

(defmethod ig/init-key ::server [_ {:keys [service-map]}]
  (print "Pedestal Server starting...")
  (let [server (-> service-map
                   http/default-interceptors
                   http/dev-interceptors
                   http/create-server
                   http/start)]
    (println (str "running on port " (:io.pedestal.http/port service-map)))
    server))

(defmethod ig/halt-key! ::server [_ service]
  (println "\nStopping Pedestal Service")
  (http/stop service))
