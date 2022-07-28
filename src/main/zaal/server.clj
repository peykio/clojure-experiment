(ns zaal.server
  (:require [zaal.router :as router]
            [zaal.pathom]
            [muuntaja.middleware :as middleware]
            [integrant.core :as ig]))

;; (defn app
;;   [env]
;;   (router/routes env))

(defn app
  [_]
  (-> zaal.pathom/pathom-handler
      (middleware/wrap-format zaal.pathom/muuntaja-options)))

(defmethod ig/init-key ::app
  [_ config]
  (println "\nStarted app")
  (app config))