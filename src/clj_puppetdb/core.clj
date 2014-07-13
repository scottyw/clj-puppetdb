(ns clj-puppetdb.core
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(defn connect
  "Placeholder for now, but eventually this will bundle up all the authentication
  details necessary to connect to the PuppetDB server, including certs."
  [^String url]
  url)

(defn facts
  "GET the response from the /v3/facts endpoint as a seq of maps."
  [connection]
  (-> (str connection "/v3/facts")
       http/get
       :body
       (json/decode keyword)))
