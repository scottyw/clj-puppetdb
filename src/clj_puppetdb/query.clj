(ns clj-puppetdb.query
  (:require [clojure.walk :refer [postwalk]]
            [cheshire.core :as json]))

(def ops
  {:match "~"
   :nil? "null?"})

(defn- operator?
  [x]
  (contains? ops x))

(defn query-walk
  "This does most of the hard work for generating a query.
  It's intended to be applied recursively to each and every
  element of an expression to ensure that the final result
  can be converted straightforwardly into JSON. Just about
  anything that can be turned into a string will be, but
  booleans and numbers pass through unchanged."
  [x]
  (cond
   (operator? x) (ops x)
   (instance? java.util.regex.Pattern x) (str x)
   :else x))

(defn query
  "Takes a vector approximating an API query (may include some conveniences
  like Clojure regex literals and the :match keyword) and converts it into
  JSON suitable for the API. Does not url-encode the query."
  [q]
  (json/encode (postwalk query-walk q)))
