(ns clojure-experiment.router
  (:require
   [clojure-experiment.account.routes :as account]
   [clojure-experiment.conversation.routes :as conversation]
   [clojure-experiment.middleware :as mw]
   [clojure-experiment.recipe.routes :as recipe]
   [muuntaja.core :as m]
   [reitit.coercion.spec :as coercion-spec]
   [reitit.dev.pretty :as pretty]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.dev :as dev]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.spec :as rs]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]))

(def swagger-docs
  ["/swagger.json"
   {:get
    {:no-doc true
     :swagger {:basePath "/"
               :info {:title "Zaal API Reference"
                      :description "The Zaal API is organized around REST. Returns JSON, Transit (msgpack, json), or EDN  encoded responses."
                      :version "1.0.0"}}
     :handler (swagger/create-swagger-handler)}}])

(defn router-config
  [env]
  {:validate rs/validate
   ;:reitit.middleware/transform dev/print-request-diffs
   :exception pretty/exception
   :data {:env env
          :coercion coercion-spec/coercion
          :muuntaja m/instance
          :middleware [swagger/swagger-feature
                       muuntaja/format-middleware
                       ;exception/exception-middleware
                       coercion/coerce-request-middleware
                       coercion/coerce-response-middleware
                       mw/wrap-env]}})

(defn routes
  [env]
  (ring/ring-handler
   (ring/router
    [swagger-docs
     ["/v1"
      account/routes
      recipe/routes
      conversation/routes]]
    (router-config env))
   (ring/routes
    (swagger-ui/create-swagger-ui-handler {:path "/"}))))