(ns clojure-experiment.ion
  (:require [clojure-experiment.components.pedestal :as pedestal]
            [clojure-experiment.components.datomic :as datomic]
            [clojure-experiment.components.routes :as routes]
            [clojure-experiment.pathom :as pathom]
            [integrant.core :as ig]))

(def system-map {::pedestal/ion-server {:service-map {:io.pedestal.http/routes (ig/ref ::routes/routes)}}
                 ::routes/routes {:pathom-env (ig/ref ::pathom/env)}
                 ::pathom/env {:datomic (ig/ref ::datomic/db)}
                 ::datomic/db {:server-type :ion
                               :region "us-east-1"
                               :system "zaal-prod"
                               :db-name "zaal-db"
                               ;ClientApiGatewayEndpoint output from cloudformation stack
                               :endpoint "https://jbd4mj98ra.execute-api.us-east-1.amazonaws.com/"}})

(def system
  (delay
   (-> system-map ig/prep ig/init ::pedestal/ion-server)))


(defn handler
  [req]
  (@system req))