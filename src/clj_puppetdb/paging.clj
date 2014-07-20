(ns clj-puppetdb.paging)

;; This is a sketch to show how results can be paged through transparently
;; using lazy-seq. The `ex` map shows what the results might look like.
;; :query would/should be a fn that can be called with an offset and limit
;; to get the next set of results.

;; The lazy-page function will actually work with any map that matches
;; the spec, but this is really rough.


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
  next set of results."
  [{:keys [limit offset query]}]
  (let [new-offset (+ limit offset)]
    (println "Querying! Offset:" new-offset)
    {:body (query new-offset limit)
     :limit limit
     :offset new-offset
     :query query}))

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
