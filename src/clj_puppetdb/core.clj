(ns clj-puppetdb.core
  (:require [cemerick.url :refer [url-encode map->query]]
            [cheshire.core :as json]
            [clj-puppetdb.paging :as paging]
            [clj-puppetdb.query :as q]
            [clj-puppetdb.schema :refer [Client]]
            [clj-puppetdb.util :refer [file?]]
            [puppetlabs.http.client.sync :as http]
            [schema.core :as s]))

(defn- make-https-client
  [^String host {:keys [ssl-ca-cert ssl-cert ssl-key]}]
  {:pre [(every? file? [ssl-ca-cert ssl-cert ssl-key])
         (.startsWith host "https://")]
   :post [(s/validate Client %)]}
  {:host host
   :opts {:ssl-ca-cert ssl-ca-cert
          :ssl-cert ssl-cert
          :ssl-key ssl-key}})

(defn- make-http-client
  [^String host]
  {:pre [(.startsWith host "http://")]
   :post [(s/validate Client %)]}
  {:host host
   :opts {}})

(defn connect
  "Return a PuppetDB client map for the given host.

  If the host begins with 'https://', you must supply also supply a map containing
  a valid :ssl-ca-cert, :ssl-cert, and :ssl-key (as java.io.File objects).
 
  If the host begins with 'http://', that must be the only argument given.

  Either way, you must specify the port as part of the host URL (usually ':8080' for
  http or ':8081' for https)."
  ([^String host] (make-http-client host))
  ([^String host opts] (make-https-client host opts)))

(s/defn ^:always-validate GET
  "Make a GET request using the given PuppetDB client, returning the results
  as a lazy sequence of maps with keywordized keys.

  The `path` argument must be a URL-encoded string.

  You may provide a set of querystring parameters as a map. These will be url-encoded
  automatically and added to the path."
  ([client :- Client ^String path]
     (println "GET:" path)
     (let [{:keys [host opts]} client]
       (-> (http/get (str host path) opts)
           :body
           (json/decode keyword))))
  ([client path params]
     (let [query-params (map->query params)
           new-path (str path "?" query-params)]
       (GET client new-path))))

(defn query
  "Use the given PuppetDB client to query the server.
  
  The path argument should be a valid endpoint, e.g. \"/v4/nodes\".

  The query-vec argument should be a vector representing an API query,
  e.g. [:= [:fact \"operatingsystem\"] \"Linux\"]"
  ([client path query-vec]
     (let [query-string (q/query->json query-vec)]
       (GET client path {:query query-string})))
  ([client path] (GET client path)))

(defn lazy-query
  "Return a lazy sequence of results from the given query. Unlike the regular
  `query` function, `lazy-query` uses paging to fetch results gradually as they
  are consumed.
  
  The `params` map is required, and should contain the following keys:
  - :limit (the number of results to request)
  - :offset (optional: the index of the first result to return, default 0)
  - :order-by (a vector of maps, each specifying a :field and an :order key)
  For example: `{:limit 100 :offset 0 :order-by [{:field \"value\" :order \"asc\"}]}`"
  ([client path params]
     (paging/lazy-query client path params))
  ([client path query-vec params]
     (paging/lazy-query client path query-vec params)))

(comment

(require '[clojure.java.io :as io]
         '[clojure.pprint :refer [pprint]])

(def client
  (connect "https://puppetdb:8081"
           {:ssl-ca-cert (io/file "/Users/justin/certs/ca.pem")
            :ssl-cert (io/file "/Users/justin/certs/cert.pem")
            :ssl-key (io/file "/Users/justin/certs/private.pem")}))
  )
