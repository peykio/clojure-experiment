(ns clojure-experiment.pedestal.datomic
  (:require [clojure-experiment.pedestal.helpers :refer [get-or-fail]]
            [datomic.client.api :as d]))

(def ^:private prepare-params
  "Given a parameter map containing Datomic Ion params pull out the relevant values needed for starting a Datomic client."
  (memoize (fn [params]
             {:db-name (get-or-fail params :DATOMIC_DB_NAME)})))

(def datomic-params-interceptor
  "Puts params related to Datomic on context."
  {:name ::datomic-params-interceptor
   :enter (fn [context]
            (let [params (-> :io.pedestal.ions/params context prepare-params)]
              (assoc context ::params params)))})

(def datomic-client-interceptor
  {:name ::datomic-client-interceptor
   :enter (fn [context]
            (let [client (d/client {:server-type :ion
                                    :region "us-east-1"
                                    :system "zaal-prod"
                                    ;ClientApiGatewayEndpoint output from cloudformation stack
                                    :endpoint "https://jbd4mj98ra.execute-api.us-east-1.amazonaws.com/"})]
              (assoc context ::client client)))})

(def datomic-conn-interceptor
  {:name ::datomic-conn-interceptor
   :enter (fn [context]
            (let [datomic-params (get context ::params)
                  conn (d/connect (get context ::client) (select-keys datomic-params [:db-name]))]
              (assoc context ::conn conn)))})

(def datomic-db-interceptor
  {:name ::datomic-db-interceptor
   :enter (fn [context]
            (let [db (d/db (get context ::conn))]
              (assoc context ::db db)))})
