(ns clojure-experiment.server
  (:require [clojure-experiment.router :as router]
            [clojure-experiment.pathom]
            [muuntaja.middleware :as middleware]
            [integrant.core :as ig]))

(defn app
  [env]
  (router/routes env))

;; (defn app
;;   [_]
;;   (-> clojure-experiment.pathom/pathom-handler
;;       (middleware/wrap-format clojure-experiment.pathom/muuntaja-options)))

(defmethod ig/init-key ::app
  [_ config]
  (println "\nStarted app")
  (app config))