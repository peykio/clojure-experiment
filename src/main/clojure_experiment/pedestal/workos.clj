(ns clojure-experiment.pedestal.workos
  (:require [clj-http.lite.client :as http]
            [io.pedestal.ions :as provider]
            [muuntaja.core :as m]
            [muuntaja.interceptor :as muuntaja]))

(def workos-organizations
  {:name ::workos-organizations
   :enter (fn [context]
            (let [res (http/get "https://api.workos.com/organizations"
                                {:headers {"Authorization" (str "Bearer " (get-in context [:io.pedestal.ions/params :WORKOS_CLIENT_SECRET]))}})]
              (assoc context :response {:status 200 :body (m/decode "application/json" (:body res))})))})

(def workos-passwordless
  {:name ::workos-passwordless
   :enter (fn [context]
            (let [params (get-in context [:request :body-params])
                  res  (http/post "https://api.workos.com/passwordless/sessions"
                                  {:throw-exceptions false
                                   :body (m/encode "application/json" {:type "MagicLink"
                                                                       :email (:email params)})
                                   :headers {"content-type" "application/json"
                                             "Authorization" (str "Bearer " (get-in context [:io.pedestal.ions/params :WORKOS_CLIENT_SECRET]))}})]

              (if (not= (:status res) 200)
                (let [body (->> res :body (m/decode "application/json"))]
                  (http/post (str "https://api.workos.com/passwordless/sessions/" (:id body) "/send")
                             {:throw-exceptions false
                              :headers {"Authorization" (str "Bearer " (get-in context [:io.pedestal.ions/params :WORKOS_CLIENT_SECRET]))}})
                  (assoc context :response {:status (:status res) :body body}))
                (assoc context :response res))))})

(def workos-sso-authorize
  {:name ::workos-sso-authorize
   :enter (fn [context]
            (let [res (http/get "https://api.workos.com/sso/authorize"
                                {:follow-redirects false
                                 :query-params {:response_type "code"
                                                :client_id (get-in context [:io.pedestal.ions/params :WORKOS_CLIENT_ID])
                                                :redirect_uri "https://xpznriu6ek.execute-api.us-east-1.amazonaws.com/api/workos/sso/token"
                                                :provider "GoogleOAuth"}
                                 :headers {"Authorization" (str "Bearer " (get-in context [:io.pedestal.ions/params :WORKOS_CLIENT_SECRET]))}})]
              (assoc context :response res)))})

(def workos-sso-token
  {:name ::workos-sso-token
   :enter (fn [context]
            (let [query (get-in context [:request :query-params])
                  res  (http/post "https://api.workos.com/sso/token"
                                  {:query-params {:grant_type "authorization_code"
                                                  :client_id (get-in context [:io.pedestal.ions/params :WORKOS_CLIENT_ID])
                                                  :client_secret (get-in context [:io.pedestal.ions/params :WORKOS_CLIENT_SECRET])
                                                  :code (:code query)}})]
              (assoc context :response {:status 302 :body "Found. Redirecting to <a href=\"https://xpznriu6ek.execute-api.us-east-1.amazonaws.com/done\"/>" :headers {"Location" (str "https://xpznriu6ek.execute-api.us-east-1.amazonaws.com/done?" (->> res :body (m/decode "application/json") :profile :first_name))}})))})

(def routes
  #{["/api/workos/organizations" :get
     [(muuntaja/format-interceptor)
      (provider/datomic-params-interceptor)
      workos-organizations]
     :route-name ::workos-organizations]
    ["/api/workos/sso/authorize" :get
     [(muuntaja/format-interceptor)
      (provider/datomic-params-interceptor)
      workos-sso-authorize]
     :route-name ::workos-sso-authorize]
    ["/api/workos/sso/token" :get
     [(muuntaja/format-interceptor)
      (provider/datomic-params-interceptor)
      workos-sso-token]
     :route-name ::workos-sso-token]
    ["/api/workos/passwordless" :post
     [(muuntaja/format-interceptor)
      (provider/datomic-params-interceptor)
      workos-passwordless]
     :route-name ::workos-passwordless]})