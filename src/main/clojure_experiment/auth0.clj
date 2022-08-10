(ns clojure-experiment.auth0
  (:require [clj-http.client :as http]
            [muuntaja.core :as m]))

(defn get-management-token
  [auth0]
  (->> {:throw-exceptions false
        :content-type :json
        :cookie-policy :standard
        :body (m/encode "application/json"
                        {:client_id ""
                         :client_secret (:client-secret auth0)
                         :audience ""
                         :grant_type "client_credentials"})}
       (http/post "")
       (m/decode-response-body)
       :access_token))


(defn get-role-id
  [token]
  (->> {:headers {"Authorization" (str "Bearer " token)}
        :throw-exceptions false
        :content-type :json
        :cookie-policy :standard}
       (http/get "")
       (m/decode-response-body)
       (filter (fn [role] (= (:name role) "manage-recipes")))
       (first)
       :id))