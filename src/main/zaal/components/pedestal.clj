(ns zaal.components.pedestal
  (:require [integrant.core :as ig]
            [io.pedestal.http :as http]))

(defmethod ig/init-key ::server
  [_ {:keys [service-map] :as _config}]
  (print (str "Pedestal Server starting..."))
  (let [server (http/start
                (http/create-server service-map))]
    (println (str "running on port " (get service-map :io.pedestal.http/port)))
    server))

(defmethod ig/halt-key! ::server
  [name service]
  (println "\nStopping Pedestal Service")
  (println name service)
  (http/stop service))
