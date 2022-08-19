(ns user
  (:require [clojure.core.async :as a :refer (<! >! >!! go-loop to-chan! to-chan!!)]
            [datomic.client.api :as d]
            [datomic.dev-local :as dl]
            [integrant.core :as ig]
            [integrant.repl :as ig-repl]
            [integrant.repl.state :as state]
            [clojure-experiment.ion :as ion]))


(dl/divert-system {:system "zaal-prod"})

(defn set-prep! []
  (let [dev-overrides (-> "config/system-map-dev-overrides.edn" slurp ig/read-string)
        system-map-dev (-> ion/system-map
                           (dissoc :clojure-experiment.components.pedestal/ion-server)
                           (merge dev-overrides))]
    (ig/load-namespaces  system-map-dev)
    system-map-dev))

(ig-repl/set-prep! set-prep!)

(def start-dev ig-repl/go)
(def stop-dev ig-repl/halt)
(def restart-dev (do ig-repl/halt ig-repl/go))
(def reset-all ig-repl/reset-all)

(def app (-> state/system :clojure-experiment.pedestal/routes))
(def datomic (-> state/system :clojure-experiment.components.datomic/db))

(defn create-file [_] {:file/file-id (random-uuid)
                       :file/type (rand-nth ["FileA" "FileB" "FileC" "FileD"])})
(defn create-specimen [_] {:specimen/specimen-id (random-uuid)
                           :specimen/type (rand-nth ["SpeciA" "SpeciB" "SpeciC"])})
(defn create-participant [_] {:participant/participant-id (random-uuid)})

(defn load-dataset
  [data]
  (let [tx #(d/transact (:conn datomic) {:tx-data %})]
    (doseq [s data]
      (tx s))))

;; https://docs.datomic.com/cloud/best.html#pipeline-transactions
(defn tx-pipeline
  "Transacts data from from-ch. Returns a map with:
     :result, a return channel getting {:error t} or {:completed n}
     :stop, a fn you can use to terminate early."
  [conn concurrency from-ch]
  (let [to-ch (a/chan 100)
        done-ch (a/chan)
        transact-data (fn [data]
                        (try
                          (d/transact conn {:tx-data data})
                        ; if exception in a transaction
                        ; will close channels and put error
                        ; on done channel.
                          (catch Throwable t
                            (.printStackTrace t)
                            (a/close! from-ch)
                            (a/close! to-ch)
                            (>!! done-ch {:error t}))))]

   ; go block prints a '.' after every 1000 transactions, puts completed
   ; report on done channel when no value left to be taken.
    (go-loop [total 0]
      (when (zero? (mod total 1000))
        (print ".") (flush))
      (if-let [c (<! to-ch)]
        (recur (inc total))
        (>! done-ch {:completed total})))

   ; pipeline that uses transducer form of map to transact data taken from
   ; from-ch and puts results on to-ch
    (a/pipeline-blocking concurrency to-ch (map transact-data) from-ch)

   ; returns done channel and a function that you can use
   ; for early termination.
    {:result done-ch
     :stop (fn [] (a/close! to-ch))}))

(comment
  (reset-all)

  (set! *print-namespace-maps* false)

  ;; 1000
  ;; n 1    "Elapsed time: 2328.086 msecs" 2.3s
  ;; n 10   "Elapsed time: 23797.311792 msecs" 23s
  ;; n 100  "Elapsed time: 372713.189541 msecs" 6.2min
  ;; 10000
  ;; n 10   "Elapsed time: 16122.530417 msecs" 16s
  ;; n 100  "Elapsed time: 336886.801458 msecs" 5.6min
  (-> (let [n 1
            files (map create-file (range (* n 100 100)))
            specimen (map create-specimen (range (* n 100)))
            participant (map create-participant (range (* n 1)))]
        (load-dataset (partition-all 10000 files))
        (load-dataset (partition-all 10000 specimen))
        (load-dataset (partition-all 10000 participant))
        (load-dataset (partition-all 10000 (map
                                            (fn [f] (merge (rand-nth specimen) {:specimen/files f}))
                                            files)))
        (load-dataset (partition-all 10000 (map
                                            (fn [s] (merge (rand-nth participant) {:participant/specimens s}))
                                            specimen))))
      time)

  ;; 1000
  ;; n 1    "Elapsed time: 33.79775 msecs" 0.033s
  ;; n 10   "Elapsed time: 1514.086083 msecs" 1.5s
  ;; n 100  "Elapsed time: 13009.469791 msecs" 13s
  ;; n 1000 "Elapsed time: 136223.674125 msecs" 2.3min
  ;; 10000
  ;; n 10   "Elapsed time: 1490.274417 msecs" 1.5s
  ;; n 100  "Elapsed time: 168802.894125 msecs" 2.8s =(
  (-> (let [n 100
            concurrency 10
            files (map create-file (range (* n 100)))
            specimens (map create-specimen (range (* n 10)))
            participants (map create-participant (range (* n 1)))]
        (tx-pipeline (:conn datomic) concurrency (to-chan!! (partition-all 1000 files)))
        (tx-pipeline (:conn datomic) concurrency (to-chan!! (partition-all 1000 specimens)))
        (tx-pipeline (:conn datomic) concurrency (to-chan! (partition-all 1000 participants)))
        (tx-pipeline (:conn datomic) concurrency (to-chan!! (partition-all 1000 (map
                                                                                 (fn [f] (merge (rand-nth specimens) {:specimen/files f}))
                                                                                 files))))
        (tx-pipeline (:conn datomic) concurrency (to-chan! (partition-all 1000 (map
                                                                                (fn [s] (merge (rand-nth participants) {:participant/specimens s}))
                                                                                specimens)))))
      time)

  ;; list participants - fast!
  (-> (d/q {:query '{:find [?p ?v]
                     :where
                     [[?p :participant/participant-id ?v]]}

            :args [(d/db (:conn datomic))]})
      time)

  ;; list participants with specimen counts - slower
  (->> (d/q {:query '[:find ?p ?v (count ?s)
                      :where
                      [?p :participant/participant-id ?v]
                      [?p :participant/specimens ?s]]

             :args [(d/db (:conn datomic))]})
       (sort-by last >)
       time)

  ;; small sample of participants
  (-> (d/q {:query '[:find (sample 10 ?p)
                     :where
                     [?p :participant/participant-id]]
            :limit 10
            :args [(d/db (:conn datomic))]}) ffirst)

  ;; count participants
  (-> (d/q '[:find (count ?p)
             :where
             [?p :participant/participant-id]]
           (d/db (:conn datomic)))
      ffirst
      time)

  ;; count specimen
  (-> (d/q '[:find (count ?s)
             :where
             [?s :specimen/specimen-id]]
           (d/db (:conn datomic)))
      ffirst
      time)

  ;; count files
  (-> (d/q '[:find (count ?f)
             :where
             [?f :file/file-id]]
           (d/db (:conn datomic)))
      ffirst
      time)

  (def participant-id (->> (d/q {:query '[:find ?v (count ?s)
                                          :where
                                          [?p :participant/participant-id ?v]
                                          [?p :participant/specimens ?s]]
                                 :args [(d/db (:conn datomic))]})
                           (sort-by last >)
                           ffirst))

  ;; get participant
  (d/q '[:find (pull ?p ["*"])
         :in $ ?participant-id
         :where
         [?p :participant/participant-id ?participant-id]]
       (d/db (:conn datomic)) participant-id)

  ;; get participant specimen file count
  (d/q '[:find ?participant-id ?s (count ?f)
         :in $ ?participant-id
         :where
         [?p :participant/participant-id ?participant-id]
         [?p :participant/specimens ?s]
         [?s :specimen/files ?f]]
       (d/db (:conn datomic)) participant-id)

  ;; participant tree -> specimen -> files
  (d/q '[:find (pull ?p [:participant/participant-id
                         {:participant/specimens [:specimen/specimen-id
                                                  :specimen/type
                                                  {:specimen/files [:file/file-id
                                                                    :file/type]}]}])
         :in $ ?participant-id
         :where
         [?p :participant/participant-id ?participant-id]]
       (d/db (:conn datomic)) participant-id)

  ;; specimen frequencies of a participant
  (d/q '[:find ?participant-id (count ?st) (frequencies ?st)
         :in $ ?participant-id
         :with ?s
         :where
         [?p :participant/participant-id ?participant-id]
         [?p :participant/specimens ?s]
         [?s :specimen/type ?st]]
       (d/db (:conn datomic)) participant-id)

  ;; specimen->file frequencies of a participant
  (-> (d/q '[:find ?participant-id (count ?ft) (frequencies ?ft)
             :in $ ?participant-id
             :with ?f
             :where
             [?p :participant/participant-id ?participant-id]
             [?p :participant/specimens ?s]
             [?s :specimen/files ?f]
             [?f :file/type ?ft]]
           (d/db (:conn datomic)) participant-id)
      time)

  ;; specimen frequencies
  (-> (d/q '[:find (count ?st) (frequencies ?st)
             :with ?s
             :where
            ;;  [?s :specimen/specimen-id _]
             [?s :specimen/type ?st]]
           (d/db (:conn datomic)))
      time)

  ;; file frequencies
  (-> (d/q '[:find (count ?ft) (frequencies ?ft)
             :with ?f
             :where
            ;;  [?f :file/file-id _] ;originally had this line and it made the query 4x slower
             [?f :file/type ?ft]]
           (d/db (:conn datomic)))
      time)


  (-> (d/q {:query '{:find [(pull ?s [:specimen/specimen-id])]
                     :in [$ ?participant-id]
                     :where [[?p :participant/participant-id ?participant-id]
                             [?p :participant/specimens ?s]]}

            :args [(d/db (:conn datomic)) participant-id]})
      time)

  (d/pull (d/db (:conn datomic)) {:eid [:account/account-id "mike@mailinator.com"]
                                  :selector '[:account/account-id
                                              :account/display-name
                                              {:account/favorite-recipes
                                               [:recipe/display-name
                                                :recipe/recipe-id]}]}))