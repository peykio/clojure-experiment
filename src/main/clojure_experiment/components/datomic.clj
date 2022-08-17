(ns clojure-experiment.components.datomic
  (:require [datomic.client.api :as d]
            [integrant.core :as ig]
            [clojure-experiment.validation :as validation]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn ident-has-attr?
  [db ident attr]
  (contains? (d/pull db {:eid ident :selector '[*]}) attr))

(defn load-dataset
  [conn]
  (let [db (d/db conn)
        tx #(d/transact conn {:tx-data %})]
    (tx (-> (io/resource "clojure_experiment/schema.edn") slurp edn/read-string))
    (when-not (ident-has-attr? db :account/account-id :db.attr/preds)
      (tx validation/attr-pred))
    (when-not (ident-has-attr? db :account/validate :db.entity/attrs)
      (tx validation/entity-attrs))))

(defmethod ig/init-key ::db
  [_ config]
  (println "\nStarted DB")
  (let [db-name (select-keys config [:db-name])
        client (d/client (select-keys config [:server-type :system :region :endpoint]))
        list-databases (d/list-databases client {})]
    (when-not (some #{(:db-name config)} list-databases)    ;; is this required?
      (d/create-database client db-name))                   ;; or will calling create-db with the same name just return true?
    (let [conn (d/connect client db-name)]
      (load-dataset conn)
      (assoc config :conn conn))))