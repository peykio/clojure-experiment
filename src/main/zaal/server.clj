(ns zaal.server
  (:require [zaal.router :as router]
            [integrant.core :as ig]))

(defn app
  [env]
  (router/routes env))

(defmethod ig/init-key ::app
  [_ config]
  (println "\nStarted app")
  (app config))