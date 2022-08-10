(ns clojure-experiment.pathom
  (:require [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
            [com.wsscode.pathom.viz.ws-connector.core :as pvc]
            [com.wsscode.pathom.viz.ws-connector.pathom3 :as p.connector]
            [com.wsscode.pathom3.plugin :as p.plugin]
            [datomic.client.api :as d]
            [integrant.core :as ig]))



(def todo-db
  {1 {:todo/id    1
      :todo/title "Write foreign docs"
      :todo/done? true}
   2 {:todo/id    2
      :todo/title "Integrate the whole internet"
      :todo/done? false}})

(pco/defresolver todo-items []
  {::pco/output
   [{:app/all-todos
     [:todo/id]}]}
  ; export only the ids to force the usage of another resolver for
  ; the details
  {:app/all-todos
   [{:todo/id 1}
    {:todo/id 2}]})

(pco/defresolver todo-by-id [{:todo/keys [id]}]
  {::pco/output
   [:todo/id
    :todo/title
    :todo/done?]}
  (get todo-db id))

(pco/defresolver constant-pi []
  {:acme.math/pi 3.14})

(pco/defresolver list-recipes [env _]
  {::pco/output
   [{:app/all-recipes
     [:recipe/recipe-id]}]}
  {:app/all-recipes (map first (d/q '[:find (pull ?e [:recipe/recipe-id])
                                      :where [?e :recipe/public? true]]
                                    (:db env)))})

(pco/defresolver get-recipe [env {:recipe/keys [recipe-id]}]
  {::pco/output
   [:recipe/recipe-id
    :recipe/prep-time
    :recipe/display-name
    :recipe/image-url
    :recipe/public?]}

  (-> (d/q '[:find (pull ?e [:recipe/recipe-id
                             :recipe/prep-time
                             :recipe/display-name
                             :recipe/image-url
                             :recipe/public?])
             :in $ ?recipe-id
             :where [?e :recipe/recipe-id ?recipe-id]]
           (:db env) recipe-id)
      ffirst))

; create a var to store the cache
(defonce plan-cache* (atom {}))

(def registry [constant-pi
               todo-items
               todo-by-id
               list-recipes
               get-recipe])

(defn env [{:keys [conn]}]
  ; persistent plan cache
  (-> {pcp/with-plan-cache plan-cache*}
      (pci/register registry)
      (p.plugin/register [(pbip/env-wrap-plugin #(assoc % :db (d/db conn)))])
      (p.connector/connect-env {::pvc/parser-id `env
                                ::p.connector/async? false})))

(defmethod ig/init-key ::env
  [_ {:keys [datomic]}]
  (println "Pathom Env")
  (env datomic))
