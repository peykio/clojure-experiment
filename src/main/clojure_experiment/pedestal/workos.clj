(ns clojure-experiment.pedestal.workos
  (:require [clj-http.lite.client :as http]
            [clojure-experiment.pedestal.helpers :refer [get-or-fail]]
            [io.pedestal.ions :as provider]
            [muuntaja.core :as m]
            [muuntaja.interceptor :as muuntaja]))

(def ^:private prepare-params
  "Given a parameter map containing Datomic Ion params pull out the relevant values needed for the WorkOS flow."
  (memoize (fn [params]
             {::client-id (get-or-fail params :WORKOS_CLIENT_ID)
              ::client-secret (get-or-fail params :WORKOS_CLIENT_SECRET)})))

(def workos-params-interceptor
  {:name ::workos-params-interceptor
   :enter (fn [context]
            (let [params (-> :io.pedestal.ions/params context prepare-params)]
              (assoc context ::params params)))})

(def workos-organizations
  {:name ::workos-organizations
   :enter (fn [context]
            (let [workos-params (get context ::params)
                  res (http/get "https://api.workos.com/organizations"
                                {:headers {"Authorization" (str "Bearer " (::client-secret workos-params))}})]
              (assoc context :response {:status 200 :body (m/decode "application/json" (:body res))})))})

(def workos-passwordless
  {:name ::workos-passwordless
   :enter (fn [context]
            (let [workos-params (get context ::params)
                  params (get-in context [:request :body-params])
                  res  (http/post "https://api.workos.com/passwordless/sessions"
                                  {:throw-exceptions false
                                   :body (m/encode "application/json" {:type "MagicLink"
                                                                       :email (:email params)})
                                   :headers {"content-type" "application/json"
                                             "Authorization" (str "Bearer " (::client-secret workos-params))}})]

              (if (not= (:status res) 200)
                (let [body (->> res :body (m/decode "application/json"))]
                  (http/post (str "https://api.workos.com/passwordless/sessions/" (:id body) "/send")
                             {:throw-exceptions false
                              :headers {"Authorization" (str "Bearer " (::client-secret workos-params))}})
                  (assoc context :response {:status (:status res) :body body}))
                (assoc context :response res))))})

(def workos-sso-authorize
  {:name ::workos-sso-authorize
   :enter (fn [context]
            (let [workos-params (get context ::params)
                  res (http/get "https://api.workos.com/sso/authorize"
                                {:follow-redirects false
                                 :query-params {:response_type "code"
                                                :client_id (::client-id workos-params)
                                                :redirect_uri "https://xpznriu6ek.execute-api.us-east-1.amazonaws.com/workos/sso/token"
                                                :provider "GoogleOAuth"}
                                 :headers {"Authorization" (str "Bearer " (::client-secret workos-params))}})]
              (assoc context :response res)))})

(def workos-sso-token
  {:name ::workos-sso-token
   :enter (fn [context]
            (let [workos-params (get context ::params)
                  query (get-in context [:request :query-params])
                  res  (http/post "https://api.workos.com/sso/token"
                                  {:query-params {:grant_type "authorization_code"
                                                  :client_id (::client-id workos-params)
                                                  :client_secret (::client-secret workos-params)
                                                  :code (:code query)}})]
              (assoc context :response {:status 302
                                        :body "Found. Redirecting to <a href=\"https://xpznriu6ek.execute-api.us-east-1.amazonaws.com/done\"/>"
                                        :headers {"Location" (str "https://xpznriu6ek.execute-api.us-east-1.amazonaws.com/done?"
                                                                  (->> res :body (m/decode "application/json")
                                                                       :profile :first_name))}})))})

(def routes
  ["/workos"
   ^:interceptors [(muuntaja/format-interceptor)
                   (provider/datomic-params-interceptor)
                   workos-params-interceptor]
   ["/organizations" {:get `workos-organizations}]
   ["/sso/authorize" {:get `workos-sso-authorize}]
   ["/sso/token" {:get `workos-sso-token}]
   ["/sso/passwordless" {:post `workos-passwordless}]])