(ns clojure-experiment.components.pedestal
  (:require [clojure-experiment.pedestal.routes :refer [routes]]
            [integrant.core :as ig]
            [io.pedestal.http :as http]
            [io.pedestal.ions :as ions]))

(defmethod ig/init-key ::ion-server []
  (-> {:env :prod
       ::http/routes routes
       ::http/resource-path "/public"
       ::http/chain-provider ions/ion-provider}
      http/default-interceptors
      http/create-provider))

(defmethod ig/init-key ::jetty-server [_ {:keys [service-map]}]
  (print "Starting local Pedestal Jetty server...")
  (let [server (-> {:env :dev
                    ::http/routes routes
                    ::http/resource-path "/public"
                    ::http/type :jetty
                    ::http/join? false
                           ;; all origins are allowed in dev mode
                    ::http/dev-allowed-origins {:creds true :allowed-origins (constantly true)}
                           ;; Content Security Policy (CSP) is mostly turned off in dev mode
                    ::http/secure-headers  {:content-security-policy-settings {:object-src "'none'"}}}
                   (merge service-map)
                   http/default-interceptors
                   http/dev-interceptors
                   http/create-server
                   http/start)]
    (println (str "running on port " (::http/port service-map)))
    server))

(defmethod ig/halt-key! ::jetty-server [_ service]
  (println "\nStopping local Pedestal Jetty server")
  (http/stop service))
