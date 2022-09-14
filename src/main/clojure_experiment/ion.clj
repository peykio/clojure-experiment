(ns clojure-experiment.ion
  (:require [clojure-experiment.components.pedestal :as pedestal]
            [integrant.core :as ig]))

(def system-map {::pedestal/ion-server {:service-map {}}})

(def system
  (delay
   (-> system-map ig/prep ig/init ::pedestal/ion-server)))

(defn handler
  [req]
  (@system req))