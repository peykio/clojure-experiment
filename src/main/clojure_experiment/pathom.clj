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
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.transform :as mt]
            [malli.error :as me]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as g]
            [exoscale.coax :as c]
            [exoscale.coax.coercer :as cc]))


(def clamp-to-range-transformer
  {:name :clamp-to-range
   :compile (fn
              [schema _]
              (let [vmin (:min (m/properties schema) java.lang.Integer/MIN_VALUE)
                    vmax (:max (m/properties schema) java.lang.Integer/MAX_VALUE)]
                (fn [v] (-> v (max vmin) (min vmax)))))})

(def LIMIT_MAX 100)
(def ?limit [:int {:min 0 :max LIMIT_MAX :default 10 :decode/clamp-to-range clamp-to-range-transformer}])
(def ?offset [:int {:min 0 :default 0 :decode/clamp-to-range clamp-to-range-transformer}])
(def ?pagination [:map {:closed true}
                  [:limit ?limit]
                  [:offset ?offset]])
(defn coercer-with-default [coercer default x]
  (let [v (coercer x nil)]
    (if (= v :exoscale.coax/invalid)
      default
      v)))
(defn clamp
  [opts x]
  (let [vmin (:min opts java.lang.Integer/MIN_VALUE)
        vmax (:max opts java.lang.Integer/MAX_VALUE)]
    (-> x (max vmin) (min vmax))))

(s/def ::limit (s/int-in 0 101))
(c/def ::limit (fn [x _] (->> x
                              (coercer-with-default cc/to-long 10)
                              (clamp {:min 0 :max 100})
                              (.longValue))))

(s/def ::offset nat-int?)
(c/def ::offset (fn [x _] (->> x
                               (coercer-with-default cc/to-long 0)
                               (clamp {:min 0})
                               (.longValue))))
(s/def ::pagination (s/keys :req-un [::limit ::offset]))
(s/def ::params (s/keys :opt-un [::limit ::offset]))



(comment
  (-> (s/keys :opt-un [::limit ::offset])
      (s/with-gen #(s/gen (s/every-kv
                           (s/or :string string? :keyword keyword? :limit #{:limit} :offset #{:offset})
                           (s/or :string string? :int int?))))
      s/gen
      g/generate)
  (m/decode ?pagination
            {:limit "122"}
            (mt/transformer
             mt/strip-extra-keys-transformer
             mt/default-value-transformer
            ;;  mt/string-transformer
             clamp-to-range-transformer))


  (s/conform ::limit -11)
  (s/explain-data ::limit "-11")
  (c/coerce ::limit nil)
  (c/coerce ::limit "11")
  (c/coerce ::limit "-11")
  (c/coerce ::limit "0")
  (c/coerce ::limit "50")
  (c/coerce ::limit "550")
  (s/conform ::offset -11)
  (c/coerce ::offset "-11")
  (c/coerce ::offset [-11])
  (s/explain-data ::pagination {::limit 122 ::offset [111]})
  (c/coerce ::pagination {:limit "-"}))

(s/fdef coax-pagination-params
  :args (s/cat :params (s/every-kv
                        #{:limit :offset :extra-key}
                        any?))
  :ret  #(s/valid? ::pagination %))
(defn coax-pagination-params [{:keys [limit offset]}] (c/coerce ::pagination {:limit limit :offset offset}))

(s/fdef decode-pagination-params
  :args (s/cat :params (s/every-kv
                        #{:limit :offset :extra-key}
                        (s/or :string string? :int int?)))
  :ret  #(s/valid? ::pagination %))
(defn decode-pagination-params [params] (m/decode ?pagination params (mt/transformer
                                                                      mt/strip-extra-keys-transformer
                                                                      mt/default-value-transformer
                                                                      mt/string-transformer
                                                                      clamp-to-range-transformer)))

(defn corce-pagination-params [params]
  (let [ps  (m/decode ?pagination params (mt/transformer
                                          mt/strip-extra-keys-transformer
                                          mt/default-value-transformer
                                          mt/string-transformer))]
    (if (m/validate ?pagination ps)
      (assoc params :pagination ps)
      (throw (ex-info (str (me/humanize (m/explain ?pagination ps))) {})))))

(defn get-params [env]
  (-> env
      pco/params
      corce-pagination-params))


(defn get-lookup-params [lookup env]
  (-> env
      get-params
      (assoc :lookup lookup)))

(defn pagination
  "Datomic returns lazy sequences which lets us use standard seq functions to mimic database limit/offset"
  [params]
  (comp
   (drop (:offset params))
   (take (:limit params))))

(defn lookup-pagination
  "The results of nested resolvers are grouped by the parent id using datalog pull syntax instead of being returned directly. This helps Pathom match results but also means we need to reach inside each returned item and set pagination values directly."
  [{:keys [lookup] :as params} e]
  (update e lookup #(into [] (-> params :pagination pagination) %)))

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
  (let [params (get-lookup-params :participant/specimens env)]
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
  (let [params (get-lookup-params :participant/specimens env)]
    (->> (d/q {:query '{:find [?participant-id (count ?s)]
                        :in [$ [?participant-id ...]]
                        :where [[?e :participant/participant-id ?participant-id]
                                [?e :participant/specimens ?s]]}
               :args [(:db env) (map :participant/participant-id items)]})
         (map (fn [[id total]] {:participant/participant-id id
                                :participant/specimens-pageinfo (assoc (:pagination params) :total total)}))
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
