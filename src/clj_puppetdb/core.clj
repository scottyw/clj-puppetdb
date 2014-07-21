(ns clj-puppetdb.core
  (:require [cemerick.url :refer [url-encode map->query]]
            [cheshire.core :as json]
            [clj-puppetdb.query :as q]
            [clj-puppetdb.schema :refer [Connection]]
            [clj-puppetdb.util :refer [file?]]
            [puppetlabs.http.client.sync :as http]
            [schema.core :as s]))

(defn- make-https-connection
  [^String host {:keys [ssl-ca-cert ssl-cert ssl-key]}]
  {:pre [(every? file? [ssl-ca-cert ssl-cert ssl-key])
         (.startsWith host "https://")]
   :post [(s/validate Connection %)]}
  {:host host
   :opts {:ssl-ca-cert ssl-ca-cert
          :ssl-cert ssl-cert
          :ssl-key ssl-key}})

(defn- make-http-connection
  [^String host]
  {:pre [(.startsWith host "http://")]
   :post [(s/validate Connection %)]}
  {:host host
   :opts {}})

(defn connect
  "Return a PuppetDB connection map for the given host.

  If the host begins with 'https://', you must supply also supply a map containing
  a valid :ssl-ca-cert, :ssl-cert, and :ssl-key (as java.io.File objects).
 
  If the host begins with 'http://', that must be the only argument given.

  Either way, you must specify the port as part of the host URL (usually ':8080' for
  http or ':8081' for https)."
  ([^String host] (make-http-connection host))
  ([^String host opts] (make-https-connection host opts)))

(s/defn ^:always-validate GET
  "Make a GET request using the given PuppetDB connection, returning the results
  as a lazy sequence of maps with keywordized keys. Doesn't support paging (yet).

  The `path` argument must be a URL-encoded string.

  You may provide a set of querystring parameters as a map. These will be added to
  the path."
  ([connection :- Connection ^String path]
     (println "GET:" path)
     (let [{:keys [host opts]} connection]
       (-> (http/get (str host path) opts)
           :body
           (json/decode keyword))))
  ([connection path params]
     (let [query-params (map->query params)
           new-path (str path "?" query-params)]
       (GET connection new-path))))

(defn query
  "Use the given PuppetDB connection to query the server.
  
  The path argument should be a valid endpoint, e.g. \"/v4/nodes\".

  The query-vec argument should be a vector representing an API query,
  e.g. [:= [:fact \"operatingsystem\"] \"Linux\"]"
  ([conn path query-vec]
     (let [query-string (q/query->json query-vec)]
       (GET conn path {:query query-string})))
  ([conn path] (GET conn path)))

(comment

(require '[clojure.java.io :as io]
         '[clojure.pprint :refer [pprint]])

(def conn
  (connect "https://puppetdb:8081"
           {:ssl-ca-cert (io/file "/Users/justin/certs/ca.pem")
            :ssl-cert (io/file "/Users/justin/certs/cert.pem")
            :ssl-key (io/file "/Users/justin/certs/private.pem")}))
  )
