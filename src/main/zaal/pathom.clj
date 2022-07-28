(ns zaal.pathom
  (:require
   [cognitect.transit :as t]
   [muuntaja.core :as muuntaja]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.connect.planner :as pcp]
   [com.wsscode.pathom3.connect.operation.transit :as pcot]
   [com.wsscode.pathom3.interface.eql :as p.eql]

   [zaal.middleware :as mw]))

; create a var to store the cache
(defonce plan-cache* (atom {}))

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
  {:acme.math/pi 3.1415})
(def env
  ; persistent plan cache
  (-> {::pcp/plan-cache* plan-cache*}
      (pci/register
       [constant-pi
        todo-items
        todo-by-id])))
(def pathom (p.eql/boundary-interface env))

(def muuntaja-options
  (update-in
   muuntaja/default-options
   [:formats "application/transit+json"]
    ; in this part we setup the read and write handlers for Pathom resolvers and mutations
   merge {:decoder-opts {:handlers pcot/read-handlers}
          :encoder-opts {:handlers  pcot/write-handlers
                          ; write-meta is required if you wanna see execution stats on Pathom Viz
                         :transform t/write-meta}}))

(defn pathom-handler [{:keys [body-params]}]
  {:status 200
   :body   (pathom body-params)})

(def routes
  ["/pathom" {:swagger {:tags ["pathom"]}
              :middleware [[mw/wrap-auth0]
                        ;;    [(middleware/wrap-format muuntaja-options)]
                           ]}
   [""
    {:get {:handler pathom-handler
           :responses {200 {:body any?}}
           :summary "Pathom"}
     :post {:handler pathom-handler
            :responses {200 {:body any?}}
            :summary "Pathom"}}]])



(comment
  ; WARNING: this will trigger a lot of requests, may take some time. Also, HN may
  ; throttle or block some accesses from you.
  (p.eql/process env
                 [:acme.math/pi])
  (pathom [:acme.math/pi]))