(ns clojure-experiment.env
  (:require [datomic.ion :as ion]))

(defn get-env [env] (get (ion/get-params {:path "/zaal/prd_aws_ion/"}) env))

(def envs {:authn {:workos-client-id (get-env "WORKOS_CLIENT_ID")
                   :workos-client-secret (get-env "WORKOS_CLIENT_SECRET")}})