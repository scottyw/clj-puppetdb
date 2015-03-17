(ns clj-puppetdb.core
  (:require [cheshire.core :as json]
            [clj-puppetdb.http :refer [GET] :as http]
            [clj-puppetdb.paging :as paging]
            [clj-puppetdb.query :as q]))

(defn connect
  "Return a PuppetDB client map for the given host.

  If the host begins with 'https://', you must supply also supply a map containing
  a valid :ssl-ca-cert, :ssl-cert, and :ssl-key (as java.io.File objects).
 
  If the host begins with 'http://', that must be the only argument given.

  Either way, you must specify the port as part of the host URL (usually ':8080' for
  http or ':8081' for https)."
  ([^String host] (connect host {}))
  ([^String host opts] (http/make-client host opts)))

(defn- encode-order-by-using-keyword
  "order-by params contain nested maps and if the VCR running we want to enforce a specific ordering to give us URL stability"
  [params client keyword]
  (if (contains? params keyword)
    (if (get-in client [:opts :vcr-dir])
      ; Stability of URL is important
      (update-in params [keyword] #(json/encode (map (fn [map] (into (sorted-map) map)) %)))
      ; Stability of URL doesn't matter
      (update-in params [keyword] json/encode))
    params))

(defn- encode-order-by
  [params client]
  (-> params
    (encode-order-by-using-keyword client :order-by)        ; v3 and earlier
    (encode-order-by-using-keyword client :order_by)))      ; v4

(defn query-with-metadata
  "Use the given PuppetDB client to query the server.

  The path argument should be a valid endpoint, e.g. \"/v4/nodes\".

  The query-vec argument is optional, and should be a vector representing an API query,
  e.g. [:= [:fact \"operatingsystem\"] \"Linux\"]

  The params map is optional, and can contain any of the following keys:
  - :order-by (a vector of maps, each specifying a :field and an :order key)
  - :limit (the number of results to request)
  - :offset (the index of the first result to return, defaults to 0)
  - :include-total (boolean indicating whether to return the total number of records available)
  For example: `{:limit 100 :offset 0 :order-by [{:field \"value\" :order \"asc\"}] :include-total true}`

  This function returns two maps in a vector. The first map is the query result as returned by 'query'. The second
  contains additional metadata. Currently the only supported kind of metadata is:
  - :total (the total number of records available)"
  ([client path params]
   (query-with-metadata client path nil params))
  ([client path query-vec params]
   (let [merged-params (merge {} params
                         (if query-vec
                           {:query (q/query->json query-vec)}))
         merged-params (encode-order-by merged-params client)
         [body headers] (GET client path merged-params)
         total         (get headers "x-records")
         metadata      (try (if total {:total (BigInteger. total)}) (catch Throwable _))]
     [body metadata])))

(defn query
  "Use the given PuppetDB client to query the server.

  The path argument should be a valid endpoint, e.g. \"/v4/nodes\".

  The query-vec argument is optional, and should be a vector representing an API query,
  e.g. [:= [:fact \"operatingsystem\"] \"Linux\"]"
  ([client path]
    (first (query-with-metadata client path nil)))
  ([client path query-vec]
    (first (query-with-metadata client path query-vec nil))))

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
