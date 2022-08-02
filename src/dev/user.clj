(ns user
  (:require [integrant.repl :as ig-repl]
            [integrant.core :as ig]
            [integrant.repl.state :as state]
            [datomic.client.api :as d]))

(ig-repl/set-prep!
 (fn []
   (let [config (-> "config/system-map-dev-local.edn" slurp ig/read-string)]
     (ig/load-namespaces config)
     config)))

(def start-dev ig-repl/go)
(def stop-dev ig-repl/halt)
(def restart-dev (do ig-repl/halt ig-repl/go))
(def reset-all ig-repl/reset-all)

(def app (-> state/system :zaal.server/app))
(def datomic (-> state/system :zaal.components.datomic-dev-local/db))

(comment

  (start-dev)

  (zaal.auth0/get-management-token (-> state/system :zaal.components.auth0/auth))
  (ig/load-namespaces
   (-> "config/dev.edn" slurp ig/read-string))

  (set! *print-namespace-maps* false)

  (d/q '[:find ?e ?v
         :where
         [?e :account/account-id ?v]]
       (d/db (:conn datomic)))


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