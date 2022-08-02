(ns zaal.ion
  (:require
   [zaal.components.datomic-cloud :as datomic-cloud]
   [zaal.components.pedestal-ion :as server]
   [zaal.pedestal :as pedestal]
   [io.pedestal.ions :as provider]
   [io.pedestal.http :as http]
   [integrant.core :as ig]))

(def system-map
  {::server/server {:service-map {:env :prod
                                  ::http/routes (ig/ref ::pedestal/app)
                                  ::http/resource-path "/public"
                                  ::http/chain-provider provider/ion-provider}}
   ::pedestal/app {:datomic (ig/ref ::datomic-cloud/db)}
   ::datomic-cloud/db {:server-type :ion
                       :region "us-east-1"
                       :system "zaal-prod"
                       :db-name "zaal-prod"
                       :endpoint "https://xpznriu6ek.execute-api.us-east-1.amazonaws.com"}})

(def app
  (delay
   (-> system-map ig/prep ig/init ::server/server)))

(defn handler
  [req]
  (@app req))