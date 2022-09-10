(ns clojure-experiment.components.routes
  (:require [clojure-experiment.pedestal.pathom :as pathom]
            [clojure-experiment.pedestal.workos :as workos]
            [clojure.set :as set]
            [integrant.core :as ig]
            [io.pedestal.http.route :as route]))

(defn routes [env]
  (route/expand-routes
   (set/union
    (pathom/routes env)
    workos/routes)))

(defmethod ig/init-key ::routes
  [_ config]
  (routes config))