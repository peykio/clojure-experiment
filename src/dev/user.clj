(ns user
  (:require [clojure.core.async :as a :refer (<! >! >!! go-loop to-chan! to-chan!!)]
            [portal.api :as p]
            [flow-storm.api :as fs-api]
            [datomic.client.api :as d]
            [datomic.dev-local :as dl]
            [integrant.core :as ig]
            [integrant.repl :as ig-repl]
            [integrant.repl.state :as state]
            [clojure-experiment.ion :as ion]
            [clj-commons.digest :as digest]))


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

;; create
(defn create-remote-storage-file
  "Create a file that has been found in remote storage."
  [uri]
  {:file/uri uri
   :file/remote-storage-uri uri})

(defn create-expected-file
  "Create an expected file."
  [uri]
  {:file/uri uri
   :file/expected-uri uri})

(defn create-specimen [_] {:specimen/specimen-id (random-uuid)
                           :specimen/type (rand-nth ["SpeciA" "SpeciB" "SpeciC"])})
(defn create-participant [_] {:participant/participant-id (random-uuid)})

;; pipeline
(defn hash-remote-storage-file
  "Generate an md5 hash using the file id."
  [f]
  (assoc f :file/remote-storage-computed-hash (digest/md5 (str (:file/uri f)))))

(defn set-file-expected-hash-match
  "Set the expected hash of the file to that of an existing remote storage file."
  [f]
  (merge f {:file/expected-hash  (digest/md5 (str (:file/uri f)))}))

(defn set-file-expected-hash-mismatch
  "Generate an expected hash that will not match any remote storage file."
  [f]
  (merge f {:file/expected-hash (digest/md5 (str (random-uuid)))}))

(defn link-file-to-specimen
  [specimen file]
  (merge specimen {:specimen/files file}))

(defn link-specimen-to-participant
  [participant specimen]
  (merge participant {:participant/specimens specimen}))

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

  ;; portal
  (def p (p/open))
  (add-tap #'p/submit)
  ;; flowstorm 
  (fs-api/local-connect)

  ;; sync
  ;; 1000
  ;; n 1    "Elapsed time: 2328.086 msecs" 2.3s
  ;; n 10   "Elapsed time: 23797.311792 msecs" 23s
  ;; n 100  "Elapsed time: 372713.189541 msecs" 6.2min
  ;; 10000
  ;; n 10   "Elapsed time: 16122.530417 msecs" 16s
  ;; n 100  "Elapsed time: 336886.801458 msecs" 5.6min

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
            uris (map (fn [_] (new java.net.URI (str "s3://us-east-1/" (random-uuid) (rand-nth [".cram" ".fastq" ".bam"])))) (range (* n 150)))
            remote-storage-files (map create-remote-storage-file (random-sample 0.7 uris))
            expected-files (map create-expected-file (random-sample 0.7 uris))
            specimens (map create-specimen (range (* n 10)))
            participants (map create-participant (range (* n 1)))]
        (tx-pipeline (:conn datomic) concurrency (to-chan!! (partition-all 1000 remote-storage-files)))
        (tx-pipeline (:conn datomic) concurrency (to-chan!! (partition-all 1000 expected-files)))
        (tx-pipeline (:conn datomic) concurrency (to-chan!! (partition-all 1000 (map hash-remote-storage-file (random-sample 0.8 remote-storage-files)))))
        (tx-pipeline (:conn datomic) concurrency (to-chan!! (partition-all 1000 (map (fn
                                                                                       [f]
                                                                                       (if (< (rand-int 10) 9)
                                                                                         (set-file-expected-hash-match f)
                                                                                         (set-file-expected-hash-mismatch f)))
                                                                                     (random-sample 0.8 expected-files)))))
        (tx-pipeline (:conn datomic) concurrency (to-chan!! (partition-all 1000 specimens)))
        (tx-pipeline (:conn datomic) concurrency (to-chan! (partition-all 1000 participants)))
        (tx-pipeline (:conn datomic) concurrency (to-chan!! (partition-all 1000 (map (fn [f] (link-file-to-specimen (rand-nth specimens) f)) (random-sample 0.7 expected-files)))))
        ;; some files can connect to multiple specimen
        (let [multi-spec-files (random-sample 0.2 expected-files)]
          (repeat 3
                  (tx-pipeline (:conn datomic) concurrency (to-chan!! (partition-all 1000 (map (fn [f] (link-file-to-specimen (rand-nth specimens) f)) multi-spec-files))))))
        (tx-pipeline (:conn datomic) concurrency (to-chan! (partition-all 1000 (map (fn [s] (link-specimen-to-participant (rand-nth participants) s)) (random-sample 0.7 specimens))))))
      time)

  ;; list participants - fast!
  (-> (d/q {:query '{:find [?p ?v]
                     :where
                     [[?p :participant/participant-id ?v]]}
            :limit 10
            :args [(d/db (:conn datomic))]})
      tap>
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
             [?f :file/uri]]
           (d/db (:conn datomic)))
      ffirst
      time)

  ;; count expected files
  (-> (d/q '[:find (count ?f)
             :where
             [?f :file/expected-uri]]
           (d/db (:conn datomic)))
      ffirst
      time)

  ;; count remote storage files
  (-> (d/q '[:find (count ?f)
             :where
             [?f :file/remote-storage-uri]]
           (d/db (:conn datomic)))
      ffirst
      time)

  ; count expected files with matching remote uri
  (-> (d/q {:query '[:find (count ?f)
                     :where
                     [?f :file/expected-uri ?eu]
                     [?f :file/remote-storage-uri ?eu]]
            :limit 1
            :args [(d/db (:conn datomic))]})
      ffirst
      time)

  ; count expected files with no remote uri
  (-> (d/q '[:find (count ?f)
             :where
             [?f :file/expected-uri]
             [(missing? $ ?f :file/remote-storage-uri)]]
           (d/db (:conn datomic)))
      ffirst
      time)

  ; count unknown remote files
  (-> (d/q '[:find (count ?f)
             :where
             [?f :file/remote-storage-uri]
             [(missing? $ ?f :file/expected-uri)]]
           (d/db (:conn datomic)))
      ffirst
      time)

  ; count expected files with matching remote uri and hash
  (-> (d/q {:query '[:find (count ?f)
                     :where
                     [?f :file/expected-uri ?eu]
                     [?f :file/remote-storage-uri ?eu]
                     [?f :file/expected-hash ?eh]
                     [?f :file/remote-storage-computed-hash ?eh]]
            :args [(d/db (:conn datomic))]})
      ffirst
      time)

  ;; matching uri not matching hash
  (-> (d/q {:query '[:find (count ?f)
                     :where
                     [?f :file/expected-uri ?eu]
                     [?f :file/remote-storage-uri ?eu]
                     (not-join [?f]
                               [?f :file/expected-hash ?eh]
                               [?f :file/remote-storage-computed-hash ?eh])]
            :args [(d/db (:conn datomic))]})
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
  (-> (d/q '[:find ?s (count ?f)
             :in $ ?participant-id
             :where
             [?p :participant/participant-id ?participant-id]
             [?p :participant/specimens ?s]
             [?s :specimen/files ?f]]
           (d/db (:conn datomic)) participant-id)
      time)

  (-> (d/q {:query '{:find [?v (count ?s) #_(pull ?e [:participant/participant-id {:participant/specimens [:specimen/specimen-id]}])]
                     :in [$]
                     :where [[?e :participant/participant-id ?v]
                             [?e :participant/specimens ?s]]}
            :limit 100
            :args [(d/db (:conn datomic))]})
      time)

  ;; participant tree -> specimen -> files
  (-> (d/q '[:find (pull ?p [:participant/participant-id
                             {[:participant/specimens :limit 10] [:specimen/specimen-id
                                                                  :specimen/type
                                                                  {[:specimen/files :limit 10] [:file/uri]}]}])
             :in $ ?participant-id
             :where
             [?p :participant/participant-id ?participant-id]]
           (d/db (:conn datomic)) participant-id)
      time)

  ;; specimen frequencies of a participant
  (-> (d/q '[:find ?participant-id (count ?st) (frequencies ?st)
             :in $ ?participant-id
             :with ?s
             :where
             [?p :participant/participant-id ?participant-id]
             [?p :participant/specimens ?s]
             [?s :specimen/type ?st]]
           (d/db (:conn datomic)) participant-id)
      time)

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

 ;; find all the participants that reference the specimens referenced by participant-id
  (-> (d/q {:query '{:find [(pull ?s [{:participant/_specimens [:participant/participant-id]} :specimen/specimen-id])]
                     :in [$ ?participant-id]
                     :where [[?p :participant/participant-id ?participant-id]
                             [?p :participant/specimens ?s]]}

            :args [(d/db (:conn datomic)) participant-id]})
      time)

  ;; file frequencies
  (-> (d/q '[:find (pull ?f [:file/uri :participant/_files])
             :where
             [?p :participant/files ?f]]
           (d/db (:conn datomic)))
      time))

