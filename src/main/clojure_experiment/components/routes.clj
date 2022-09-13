(ns clojure-experiment.components.routes
  (:require [clojure-experiment.pedestal.pathom :as pathom]
            [clojure-experiment.pedestal.workos :as workos]
            [integrant.core :as ig]
            [io.pedestal.http.route :as route]))


(defn routes [config]
  [[(pathom/routes config)
    workos/routes]])

(defmethod ig/init-key ::routes
  [_ config]
  (routes config))

(comment
  ((route/url-for-routes
    (route/expand-routes (routes {:pathom-env {}})))
   ::workos/workos-sso-token))