(ns clj-puppetdb.paging
  (:require [clj-puppetdb.core :refer [connect GET]]
            [clj-puppetdb.query :as q]
            [cheshire.core :as json]))

;; TODO:
;; - The way that results are refreshed usually causes an extra query:
;;   we should only request more results if the last query returned the
;;   limit.
;; - There should be some schema checking in here
;; - This namespace is positively RIPE for refactoring.

(defn- refresh
  "Given a results map from a paging query, request and return the 
  map with the next set of results, or return nil if there are no
  more results."
  [{:keys [limit offset query]}]
  (when-let [new-body (query offset)]
    {:body new-body
     :limit limit
     :offset (+ limit offset)
     :query query}))

(defn- ensure-refreshed
  "Given a results map, refreshes it if necessary. Otherwise, return
  the results unchanged. The return value will be nil if the results
  have been exhausted."
  [results]
  (if (empty? (:body results))
    (refresh results)
    results))

(defn- lazy-page
  "Return a lazy sequence of results from the given results map,
  requesting further results from the PuppetDB server as needed."
  [results]
  (lazy-seq
    (let [refreshed (ensure-refreshed results)]
      (when (seq (:body refreshed))
        (cons
          (first (:body refreshed))
          (lazy-page (update-in refreshed [:body] rest)))))))

(defn- lazify-query
  "Returns a map containing initial results with enough contextual data
  to request the next set of results."
  ([conn path params]
     (let [json-params (update-in params [:order-by] json/encode)
           query-fn (fn [offset]
                      (GET conn path (assoc json-params :offset offset)))
           limit (:limit params)
           offset (get params :offset 0)]
       {:body nil
        :limit limit
        :offset offset
        :query query-fn}))
  ([conn path query-vec params]
     (let [params-with-query (assoc params :query (q/query->json query-vec))]
       (lazify-query conn path params-with-query))))

(defn lazy-query
  "Return a lazy sequence of results from the given query. Unlike the regular
  `query` function, `lazy-query` uses paging to fetch results gradually, making
  it especially lazy.
  
  The `params` map is required, and should contain the following keys:
  - :limit (the number of results to request)
  - :offset (optional: the index of the first result to return, default 0)
  - :order-by (a vector of maps, each specifying a :field and an :order key)
  For example: `{:limit 100 :offset 0 :order-by [{:field \"value\" :order \"asc\"}]}`"
  ([connection path params]
     (-> (lazify-query connection path params)
         lazy-page))
  ([connection path query-vec params]
     (-> (lazify-query connection path query-vec params)
         lazy-page)))

(comment

(require '[clojure.java.io :as io]
         '[clojure.pprint :refer [pprint]]
         '[clj-puppetdb.core :as pdb])
  
(def conn
  (connect "https://puppetdb:8081"
           {:ssl-ca-cert (io/file "/Users/justin/certs/ca.pem")
            :ssl-cert (io/file "/Users/justin/certs/cert.pem")
            :ssl-key (io/file "/Users/justin/certs/private.pem")}))

(def lazy-facts
  (lazy-query conn "/v4/facts"
              {:limit 100 :offset 0 :order-by [{:field :value :order "asc"}]}))

(def lazy-nodes
  (lazy-query conn "/v4/nodes"
              {:limit 1 :offset 0 :order-by [{:field :certname :order "asc"}]}))

(def facts
  (set (pdb/query conn "/v4/facts")))

)
