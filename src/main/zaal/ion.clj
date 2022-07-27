(ns zaal.ion
  (:require [integrant.core :as ig]
            [zaal.components.auth0 :as auth0]
            [zaal.components.datomic-cloud :as datomic-cloud]
            [datomic.ion :as ion]
            [zaal.server :as server]))

(def integrant-setup
  {::server/app {:datomic (ig/ref ::datomic-cloud/db)
                 :auth0 (ig/ref ::auth0/auth)}
   ::auth0/auth {:client-secret (get (ion/get-params {:path "/datomic-shared/prod/zaal/"}) "auth0-client-secret")}
   ::datomic-cloud/db {:server-type :ion
                       :region "us-east-1"
                       :system "zaal-prod"
                       :db-name "zaal-prod"
                       :endpoint "https://xpznriu6ek.execute-api.us-east-1.amazonaws.com"}})

(def app
  (delay
   (-> integrant-setup ig/prep ig/init ::server/app)))

(defn handler
  [req]
  (@app req))