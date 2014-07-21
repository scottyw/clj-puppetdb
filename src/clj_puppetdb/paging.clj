(ns clj-puppetdb.paging
  (:require [clj-puppetdb.core :refer [connect GET]]
            [cheshire.core :as json]))

;; This is a sketch to show how results can be paged through transparently
;; using lazy-seq. The `ex` map shows what the results might look like.
;; :query would/should be a fn that can be called with an offset and limit
;; to get the next set of results.

;; The lazy-page function will actually work with any map that matches
;; the spec, but this is really rough.


;; (Hypothetical) usage examples:
;; (lazy-query "/v4/facts" [:= :certname "clojure"] {:order-by [{:field :value :order :desc}] :limit 5 :offset 0})
;; (lazy-query "/v4/nodes" {:order-by [{:field :value :order :desc}] :limit 5 :offset 0})
;; 
;; The real difference here (obviously) is the extra map at the end. The only thing here that should be optional is :offset. The rest really should be explicit.
;;
;; Some other notes:
;;  * :order-by has to be JSON-encoded first. It works just fine in the REPL so far.
;;  * Make sure to check for actual results! The lazy-seq won't be infinite. It should end.

(def ex
  {:body '(1 2 3 4 5)
   :limit 5
   :offset 0
   :query (fn [offset limit] (range offset (+ offset limit)))})

(defn falling-behind?
  "Returns true if the results map contains <= 1 result."
  [results]
  (>= 1
      (count (:body results))))

(defn refresh
  "Given a results map from a paging query, request and return the 
  next set of results, or the empty list if there are no results."
  [{:keys [limit offset query]}]
  (let [new-offset (+ limit offset)]
    (println "Querying! Offset:" new-offset)
    (if-let [new-body (query new-offset limit)]
      {:body new-body
       :limit limit
       :offset new-offset
       :query query}
      ;; return the empty list if there are no new results
      ())))

(defn refreshed
  "Given a results map, refreshes it if necessary. Otherwise, return
  the results without the first element in the body."
  [results]
  (if (falling-behind? results)
    (refresh results)
    (update-in results [:body] rest)))

(defn lazy-page
  [results]
  (lazy-seq
    (cons
      (first (:body results))
      (lazy-page (refreshed results)))))


(comment

;; The status here is that the code mostly works, with the following caveats:
;;  * The lazy-seq doesn't stop. It starts emitting nil when the results run out,
;;    instead of just shutting down.
;;  * The :offset key should be optional, but it's not. I think I was just associng
;;    it in the wrong spot. I might have even fixed it by now...
;;  * There should be a function that ties the whole thing together, including the
;;    query-vec. I'm thinking lazy-query.
  
(require '[clojure.java.io :as io]
         '[clojure.pprint :refer [pprint]])
  
(def conn
  (connect "https://puppetdb:8081"
           {:ssl-ca-cert (io/file "/Users/justin/certs/ca.pem")
            :ssl-cert (io/file "/Users/justin/certs/cert.pem")
            :ssl-key (io/file "/Users/justin/certs/private.pem")}))

(defn lazify-query
  "Skip the query-vec for now.
  In: conn, endpoint, config-map
  Out: lazy-query-map with results initialized"
  [conn path params]
  (let [json-params (update-in params [:order-by] json/encode)
        query-fn (fn [offset limit]
                   (GET conn path (assoc json-params :offset offset)))
        limit (:limit params)
        offset (get params :offset 0)]
    {:body (query-fn offset limit)
     :limit limit
     :offset offset
     :query query-fn}))

(def lazy-fact-map
  (lazify-query conn "/v4/facts" {:limit 5 :offset 0 :order-by [{:field :value :order "asc"}]}))

(def lazy-facts
  (lazy-page lazy-fact-map))

)
