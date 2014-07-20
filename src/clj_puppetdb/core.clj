(ns clj-puppetdb.core
  (:require [cemerick.url :refer [url-encode]]
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

  Either way, you must specify the port as part of the hostname (usually ':8080' for
  http or ':8081' for https)."
  ([^String host] (make-http-connection host))
  ([^String host opts] (make-https-connection host opts)))

(s/defn ^:always-validate GET
  "Make a GET request using the given PuppetDB connection, returning the results
  as a lazy sequence of maps with keyword keys. Doesn't support paging (yet).

  The `path` argument should be a URL-encoded string."
  [connection :- Connection ^String path]
  (let [{:keys [host opts]} connection]
    (-> (http/get (str host path) opts)
        :body
        (json/decode keyword))))

(defn query
  ([conn path query-vec]
     (let [query-string (-> query-vec q/query url-encode)
           url (str path "?query=" query-string)]
       (println "Querying:" url)
       (GET conn url)))
  ([conn path] (GET conn path)))

(comment
(def conn
  (connect "https://puppetdb:8081"
           {:ssl-ca-cert (io/file "/Users/justin/certs/ca.pem")
            :ssl-cert (io/file "/Users/justin/certs/cert.pem")
            :ssl-key (io/file "/Users/justin/certs/private.pem")}))
  )
