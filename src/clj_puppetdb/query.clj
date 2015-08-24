(ns clj-puppetdb.query
  (:require [clojure.walk :refer [postwalk]]
            [cheshire.core :as json])
  (:import [java.util.regex Pattern]))

(def ops
  {:match "~"
   :nil? "null?"})

(defn- operator?
  [x]
  (contains? ops x))

(defn- query-walk
  "This does most of the hard work for generating a query.
  It's intended to be applied recursively to each and every
  element of an expression to ensure that the final result
  can be converted straightforwardly into JSON. Just about
  anything that can be turned into a string will be, but
  booleans and numbers pass through unchanged."
  [x]
  (cond
    (operator? x) (ops x)
    (instance? Pattern x) (str x)
    :else x))

(defn canonicalize-query
  "Takes a vector approximating an API query (may include some conveniences
  like Clojure regex literals and the :match keyword) and converts it into
  a form suitable for the API."
  [q]
  (postwalk query-walk q))

(def json-params
  "Parameters requiring JSON encoding."
  ; TODO remove :order-by and :counts-filter when we drop support for PDB API older than v4
  [:query :order_by :counts_filter :order-by :counts-filter])

(defn params->json
  "Takes a map of PDB request parameters and encodes those parameters which
  require it into JSON."
  [params]
  (reduce
    (fn [params key]
      (if (contains? params key)
        (let [value (get params key)]
          ; if the value is a string then we assume it is already JSON encoded
          (if (string? value)
            params
            (->> value
                 json/encode
                 (assoc params key))))
        params))
    params
    json-params))
