(ns clojure-experiment.pathom
  (:require [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
            [com.wsscode.pathom.viz.ws-connector.core :as pvc]
            [com.wsscode.pathom.viz.ws-connector.pathom3 :as p.connector]
            [com.wsscode.pathom3.plugin :as p.plugin]
            [com.wsscode.misc.coll :as coll]
            [datomic.client.api :as d]
            [integrant.core :as ig]))

;; request query
;; (-> env
;;     :com.wsscode.pathom3.connect.planner/graph
;;     :com.wsscode.pathom3.connect.planner/index-ast
;;     :app/list-participants
;;     :query)

#trace
 (defn get-params [lookup env]
   (-> env
       pco/params
       (assoc :lookup lookup)))

(defn get-pagination-params [params]
  {:limit (:limit params 10)
   :offset (:offset params 0)})

(defn pagination
  "Datomic returns lazy sequences which lets us use standard seq functions to mimic database limit/offset"
  [params]
  (let [pagination-params (-> params get-pagination-params)]
    (comp
     (drop (:offset pagination-params))
     (take (:limit pagination-params)))))

(defn lookup-pagination
  "The results of nested resolvers are grouped by the parent id using datalog pull syntax instead of being returned directly. This helps Pathom match results but also means we need to reach inside each returned item and set pagination values directly."
  [{:keys [lookup] :as params} e]
  (update e lookup #(into [] (pagination params) %)))

(defn lookup-pipeline
  [params]
  (comp
   (map first)
   (map #(lookup-pagination params %))))

(pco/defresolver list-participants-fast [env _]
  {::pco/output [{:app/list-participants-fast
                  [:participant/participant-id
                   {:participant/specimens
                    [:specimen/specimen-id
                     :specimen/type
                     {:specimen/files
                      [:file/url
                       :file/expected-uri
                       :file/expected-hash
                       :file/remote-storage-uri
                       :file/remote-storage-computed-hash]}]}]}]}
  {:app/list-participants-fast
   (->> (d/q {:query '{:find [(pull ?e [:participant/participant-id
                                        {[:participant/specimens :limit 10]
                                         [:specimen/specimen-id
                                          :specimen/type
                                          {[:specimen/files :limit 10]
                                           [:file/url
                                            :file/expected-uri
                                            :file/expected-hash
                                            :file/remote-storage-uri
                                            :file/remote-storage-computed-hash]}]}])]
                       :where [[?e :participant/participant-id]]}
              :limit 10
              :args [(:db env)]})
        (mapv first))})

(pco/defresolver list-participants [env _]
  {::pco/output
   [{:app/list-participants [:participant/participant-id]}]}
  {:app/list-participants
   (->> (d/q {:query '{:find [(pull ?e [:participant/participant-id])]
                       :where [[?e :participant/participant-id]]}
              :limit 10
              :args [(:db env)]})
        (mapv first))})

(pco/defresolver get-participant [env items]
  {::pco/input [:participant/participant-id]
   ::pco/output [:participant/participant-id :participant/field]
   ::pco/batch? true}
  (->> (d/q {:query '{:find [(pull ?e [:participant/participant-id])]
                      :in [$ [?participant-id ...]]
                      :where [[?e :participant/participant-id ?participant-id]]}
             :limit 10
             :args [(:db env) (map :participant/participant-id items)]})
       (mapv first)
       (coll/restore-order2 items :participant/participant-id)))

(pco/defresolver list-participant-specimens [env items]
  {::pco/input [:participant/participant-id]
   ::pco/output [{:participant/specimens [:specimen/specimen-id]}]
   ::pco/batch? true}
  (let [params (get-params :participant/specimens env)]
    (->> (d/q {:query '{:find [(pull ?e [:participant/participant-id {:participant/specimens [:specimen/specimen-id]}])]
                        :in [$ [?participant-id ...]]
                        :where [[?e :participant/participant-id ?participant-id]]}
               :args [(:db env) (map :participant/participant-id items)]})
         (sequence (lookup-pipeline params))
         vec
         (coll/restore-order2 items :participant/participant-id))))


(pco/defresolver pageinfo-participant-specimens [env items]
  {::pco/input [:participant/participant-id]
   ::pco/output [{:participant/specimens-pageinfo [:total :limit :offset]}]
   ::pco/batch? true}
  (let [params (get-params :participant/specimens env)]
    (->> (d/q {:query '{:find [?participant-id (count ?s)]
                        :in [$ [?participant-id ...]]
                        :where [[?e :participant/participant-id ?participant-id]
                                [?e :participant/specimens ?s]]}
               :args [(:db env) (map :participant/participant-id items)]})
         (map (fn [[id total]] {:participant/participant-id id
                                :participant/specimens-pageinfo (assoc (get-pagination-params params) :total total)}))
         vec
         (coll/restore-order2 items :participant/participant-id))))



(pco/defresolver get-specimen [env items]
  {::pco/input [:specimen/specimen-id]
   ::pco/output [:specimen/specimen-id
                 :specimen/type]
   ::pco/batch? true}
  (->> (d/q {:query '{:find [(pull ?e [:specimen/specimen-id
                                       :specimen/type])]
                      :in [$ [?specimen-id ...]]
                      :where [[?e :specimen/specimen-id ?specimen-id]]}
             :args [(:db env) (map :specimen/specimen-id items)]})
       (mapv first)
       (coll/restore-order2 items :specimen/specimen-id)))

(pco/defresolver list-specimen-files [env items]
  {::pco/input [:specimen/specimen-id]
   ::pco/output [{:specimen/files [:file/uri]}]
   ::pco/batch? true}
  (->> (d/q {:query '{:find [(pull ?e [:specimen/specimen-id {:specimen/files [:file/uri]}])]
                      :in [$ [?specimen-id ...]]
                      :where [[?e :specimen/specimen-id ?specimen-id]]}
             :args [(:db env) (map :specimen/specimen-id items)]})
       (mapv first)
       (coll/restore-order2 items :specimen/specimen-id)))

(pco/defresolver get-file [env items]
  {::pco/input [:file/uri]
   ::pco/output [:file/uri
                 :file/expected-uri
                 :file/expected-hash
                 :file/remote-storage-uri
                 :file/remote-storage-computed-hash]
   ::pco/batch? true}
  (->> (d/q {:query '{:find [(pull ?e [:file/uri
                                       :file/expected-uri
                                       :file/expected-hash
                                       :file/remote-storage-uri
                                       :file/remote-storage-computed-hash])]
                      :in [$ [?file-uri ...]]
                      :where [[?e :file/uri ?file-uri]]}
             :args [(:db env) (map :file/uri items)]})
       (mapv first)
       (coll/restore-order2 items :file/uri)))


; create a var to store the cache
(defonce plan-cache* (atom {}))

(def registry [list-participants-fast
               list-participants
               get-participant
               list-participant-specimens
               pageinfo-participant-specimens
               get-specimen
               list-specimen-files
               get-file])

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
