(ns clojure-experiment.pedestal.routes
  (:require [clojure-experiment.pedestal.pathom :as pathom]
            [clojure-experiment.pedestal.workos :as workos]
            [io.pedestal.http.route :as route]))

(def routes
  [[pathom/routes
    workos/routes]])

(comment
  ((route/url-for-routes
    (route/expand-routes routes))
   ::workos/workos-sso-token))