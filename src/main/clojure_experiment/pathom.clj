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

;; request query
;; (-> env
;;     :com.wsscode.pathom3.connect.planner/graph
;;     :com.wsscode.pathom3.connect.planner/index-ast
;;     :app/list-participants
;;     :query)

(pco/defresolver list-participants [env _]
  {::pco/output
   [{:app/list-participants
     [:participant/participant-id
      {:participant/specimens [:specimen/specimen-id
                               :specimen/type
                               {:specimen/files [:file/url
                                                 :file/expected-uri
                                                 :file/expected-hash
                                                 :file/remote-storage-uri
                                                 :file/remote-storage-computed-hash]}]}]}]}
  {:app/list-participants

   (map first (d/q {:query '{:find [(pull ?e [:participant/participant-id
                                              {[:participant/specimens :limit 10] [:specimen/specimen-id
                                                                                   :specimen/type
                                                                                   {[:specimen/files :limit 10] [:file/url
                                                                                                                 :file/expected-uri
                                                                                                                 :file/expected-hash
                                                                                                                 :file/remote-storage-uri
                                                                                                                 :file/remote-storage-computed-hash]}]}])]
                             :where [[?e :participant/participant-id]]}
                    :limit 10
                    :args [(:db env)]}))})

; create a var to store the cache
(defonce plan-cache* (atom {}))

(def registry [list-participants])

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
