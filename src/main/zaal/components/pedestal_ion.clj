(ns zaal.components.pedestal-ion
  (:require [integrant.core :as ig]
            [io.pedestal.http :as http]))

(defn handler
  [service-map]
  (-> service-map
      http/default-interceptors
      http/create-provider))

(defmethod ig/init-key ::server
  [_ {:keys [service-map]}]
  (handler service-map))

