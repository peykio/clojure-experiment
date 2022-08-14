(ns user
  (:require [datomic.client.api :as d]
            [datomic.dev-local :as dl]
            [integrant.core :as ig]
            [integrant.repl :as ig-repl]
            [integrant.repl.state :as state]
            [clojure-experiment.ion :as ion]))


(dl/divert-system {:system "zaal-prod"})

(ig-repl/set-prep!
 (fn []
   (let [dev-overrides (-> "config/system-map-dev-overrides.edn" slurp ig/read-string)
         system-map-dev (-> ion/system-map
                            (dissoc :clojure-experiment.components.pedestal/ion-server)
                            (merge dev-overrides))]
     (ig/load-namespaces  system-map-dev)
     system-map-dev)))

(def start-dev ig-repl/go)
(def stop-dev ig-repl/halt)
(def restart-dev (do ig-repl/halt ig-repl/go))
(def reset-all ig-repl/reset-all)

(def app (-> state/system :clojure-experiment.pedestal/routes))
(def datomic (-> state/system :clojure-experiment.components.datomic/db))

(comment

  (start-dev)

  (clojure-experiment.auth0/get-management-token (-> state/system :clojure-experiment.components.auth0/auth))
  (ig/load-namespaces
   (-> "config/dev.edn" slurp ig/read-string))

  (set! *print-namespace-maps* false)

  (d/q '[:find ?e ?v
         :where
         [?e :account/account-id ?v]]
       (d/db (:conn datomic)))

  (-> (d/q '[:find (pull ?e [:recipe/recipe-id
                             :recipe/prep-time
                             :recipe/display-name
                             :recipe/image-url
                             :recipe/public?])
             :in $ ?recipe-id
             :where [?e :recipe/recipe-id ?recipe-id]]
           (d/db (:conn datomic)) #uuid "471c09cd-d303-4656-a380-1c41dcf096db")
      ffirst)

  (d/q '[:find ?e ?v ?display-name
         :in $ ?account-id
         :where
         [?e :recipe/recipe-id ?v]
         [?e :recipe/display-name ?display-name]
         [?e :recipe/owner ?account-id]]
       (d/db (:conn datomic)) [:account/account-id "mike@mailinator.com"])

  (d/pull (d/db (:conn datomic)) {:eid [:account/account-id "mike@mailinator.com"]
                                  :selector '[:account/account-id
                                              :account/display-name
                                              {:account/favorite-recipes
                                               [:recipe/display-name
                                                :recipe/recipe-id]}]}))