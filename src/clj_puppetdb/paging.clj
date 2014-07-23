(ns clj-puppetdb.paging
  (:require [clj-puppetdb.core :refer [connect GET]]
            [clj-puppetdb.query :as q]
            [cheshire.core :as json]))

;; TODO:
;; - The first set of results gets queried immediately.
;;   That's not that lazy.
;; - This namespace is positively RIPE for refactoring.


(defn- falling-behind?
  "Returns true if the results map contains <= 1 result."
  [results]
  (>= 1
      (count (:body results))))

(defn- refresh
  "Given a results map from a paging query, request and return the 
  next set of results."
  [{:keys [limit offset query]}]
  (let [new-offset (+ limit offset)
        new-body (query new-offset limit)]
    (println "Querying! Offset:" new-offset)
    {:body new-body
     :limit limit
     :offset new-offset
     :query query}))

(defn- refreshed
  "Given a results map, refreshes it if necessary. Otherwise, return
  the results without the first element in the body."
  [results]
  (if (falling-behind? results)
    (refresh results)
    (update-in results [:body] rest)))

(defn- lazy-page
  [results]
  (lazy-seq
    (cons
      (first (:body results))
      (let [new-results (refreshed results)] ;; can't use if-let here because this will always be truthy
        (if (seq (:body new-results)) ;; if there actually are results in there...
          (lazy-page new-results) ;; recur with them
          '()))))) ;; otherwise terminate the lazy-seq

(defn- lazify-query
  "Returns a map containing initial results with enough contextual data
  to request the next set of results."
  ([conn path params]
     (let [json-params (update-in params [:order-by] json/encode)
           query-fn (fn [offset limit]
                      (GET conn path (assoc json-params :offset offset)))
           limit (:limit params)
           offset (get params :offset 0)]
       {:body (query-fn offset limit)
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
         '[clojure.pprint :refer [pprint]])
  
(def conn
  (connect "https://puppetdb:8081"
           {:ssl-ca-cert (io/file "/Users/justin/certs/ca.pem")
            :ssl-cert (io/file "/Users/justin/certs/cert.pem")
            :ssl-key (io/file "/Users/justin/certs/private.pem")}))

(def lazy-facts
  (lazy-query conn "/v4/facts"
              {:limit 100 :offset 0 :order-by [{:field :value :order "asc"}]}))

)
